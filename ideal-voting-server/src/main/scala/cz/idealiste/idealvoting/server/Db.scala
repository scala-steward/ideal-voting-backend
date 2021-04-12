package cz.idealiste.idealvoting.server

import cats.Applicative
import cats.implicits._
import cz.idealiste.idealvoting.server.Voting._
import doobie._
import doobie.implicits._
import doobie.implicits.javatimedrivernative._
import emil.MailAddress
import emil.doobie.EmilDoobieMeta._
import zio._
import zio.interop.catz._

class Db(transactor: Transactor[Task]) {

  private implicit val han: LogHandler = LogHandler.jdkLogHandler

  private implicit lazy val mailAddressRead: Read[MailAddress] = mailAddressMulicolumnRead
  private implicit lazy val mailAddressWrite: Write[MailAddress] = mailAddressMulicolumnWrite

  def createElection(
      metadata: ElectionMetadata,
      admin: Admin,
      options: List[BallotOption],
      voters: List[Voter],
  ): Task[Unit] = {
    val commands = for {
      electionId <- Update[ElectionMetadata](
        "INSERT INTO election (title, title_mangled, description, started, ended) VALUES (?, ?, ?, ?, ?)",
      ).withUniqueGeneratedKeys[Int]("id")(metadata)
      _ <- Update[(Int, Admin)](
        "INSERT INTO admin (election_id, name, email, token) VALUES (?, ?, ?, ?)",
      ).run((electionId, admin))
      _ <- Update[(Int, BallotOption)](
        "INSERT INTO option (election_id, option_id, title, description) VALUES (?, ?, ?, ?)",
      ).updateMany(options.map((electionId, _)))
      _ <- Update[(Int, Voter)](
        "INSERT INTO voter (election_id, name, email, token, voted) VALUES (?, ?, ?, ?, ?)",
      ).updateMany(voters.map((electionId, _)))
    } yield ()
    commands.transact(transactor)
  }

  @SuppressWarnings(Array("DisableSyntax.throw"))
  def readElection(token: String): Task[Option[Election]] = {
    val commands = for {
      electionMetadataAndAdmin <-
        sql"""SELECT election.title, election.title_mangled, election.description, election.started, election.ended, admin.name, admin.email, admin.token
              FROM voter
              JOIN election ON voter.election_id = election.id
              JOIN admin ON voter.election_id = admin.election_id
              WHERE voter.token = $token""".query[(ElectionMetadata, Admin)].option
      result <- electionMetadataAndAdmin match {
        case Some((electionMetadata, admin)) =>
          for {
            options <-
              sql"""SELECT option.option_id, option.title, option.description
              FROM voter
              JOIN option ON voter.election_id = option.election_id
              WHERE voter.token = $token""".query[BallotOption].to[List]
            voters <-
              sql"""SELECT voter.name, voter.email, voter.token, voter.voted
              FROM voter AS voter0
              JOIN voter ON voter0.election_id = voter.election_id
              WHERE voter0.token = $token""".query[Voter].to[List]
            votes0 <-
              sql"""SELECT preference.vote_id, preference.option_id
              FROM voter
              JOIN vote ON voter.election_id = vote.election_id
              JOIN preference ON vote.id = preference.vote_id
              WHERE voter.token = $token
              ORDER BY preference.vote_id, preference.ordering""".query[(Int, Int)].to[List]
            optionsMap = options.map(o => (o.id, o)).toMap
            votes = votes0
              .groupBy(_._1)
              .valuesIterator
              .toList
              .traverse(preferences => Vote.makeValidated(preferences.map(_._2), optionsMap))
              .fold(es => throw es.head, identity)
          } yield Option(Election(electionMetadata, admin, options, voters, votes, optionsMap))
        case None =>
          Applicative[ConnectionIO].pure[Option[Election]](None)
      }
    } yield result
    commands.transact(transactor)
  }

  @SuppressWarnings(Array("DisableSyntax.throw"))
  def readElectionAdmin(token: String): Task[Option[Election]] = {
    val commands = for {
      electionMetadataAndAdmin <-
        sql"""SELECT election.title, election.title_mangled, election.description, election.started, election.ended, admin.name, admin.email, admin.token
              FROM admin
              JOIN election ON admin.election_id = election.id
              WHERE admin.token = $token""".query[(ElectionMetadata, Admin)].option
      result <- electionMetadataAndAdmin match {
        case Some((electionMetadata, admin)) =>
          for {
            options <-
              sql"""SELECT option.option_id, option.title, option.description
              FROM admin
              JOIN option ON admin.election_id = option.election_id
              WHERE admin.token = $token""".query[BallotOption].to[List]
            voters <-
              sql"""SELECT voter.name, voter.email, voter.token, voter.voted
              FROM admin
              JOIN voter ON admin.election_id = voter.election_id
              WHERE admin.token = $token""".query[Voter].to[List]
            votes0 <-
              sql"""SELECT preference.vote_id, preference.option_id
              FROM admin
              JOIN vote ON admin.election_id = vote.election_id
              JOIN preference ON vote.id = preference.vote_id
              WHERE admin.token = $token
              ORDER BY preference.vote_id, preference.ordering""".query[(Int, Int)].to[List]
            optionsMap = options.map(o => (o.id, o)).toMap
            votes = votes0
              .groupBy(_._1)
              .valuesIterator
              .toList
              .traverse(preferences => Vote.makeValidated(preferences.map(_._2), optionsMap))
              .fold(es => throw es.head, identity)
          } yield Option(Election(electionMetadata, admin, options, voters, votes, optionsMap))
        case None =>
          Applicative[ConnectionIO].pure[Option[Election]](None)
      }
    } yield result
    commands.transact(transactor)
  }

  def castVote(token: String, vote: Vote): Task[VoteInsertResult] = {
    val commands = for {
      votedAndElectionId <-
        sql"""SELECT voter.voted, voter.election_id
              FROM voter
              WHERE voter.token = $token""".query[(Boolean, Int)].option
      result <- votedAndElectionId match {
        case None =>
          Applicative[ConnectionIO].pure[VoteInsertResult](VoteInsertResult.TokenNotFound)
        case Some((true, _)) =>
          Applicative[ConnectionIO].pure[VoteInsertResult](VoteInsertResult.AlreadyVoted)
        case Some((false, electionId)) =>
          for {
            _ <- sql"UPDATE voter SET voted = ${true} WHERE voter.token = $token".update.run
            voteId <- sql"INSERT INTO vote (election_id) VALUES ($electionId)".update
              .withUniqueGeneratedKeys[Int]("id")
            _ <- Update[(Int, Int, Int)](
              "INSERT INTO preference (vote_id, ordering, option_id) VALUES (?, ?, ?)",
            ).updateMany(vote.preferences.zipWithIndex.map { case (BallotOption(id, _, _), ordering) =>
              (voteId, ordering, id)
            })
          } yield VoteInsertResult.SuccessfullyVoted: VoteInsertResult
      }
    } yield result
    commands.transact(transactor)
  }
}

object Db {

  def make(transactor: Transactor[Task]): Db = new Db(transactor)

  val layer: URLayer[Has[Transactor[Task]], Has[Db]] =
    ZLayer.fromService[Transactor[Task], Db](new Db(_))

}
