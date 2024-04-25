package cz.idealiste.idealvoting.server

import cats.data.Validated
import cats.implicits._
import cats.{Applicative, ApplicativeError}
import cz.idealiste.idealvoting.server
import cz.idealiste.idealvoting.server.Db._
import cz.idealiste.idealvoting.server.Voting._
import doobie._
import doobie.implicits._
import doobie.implicits.javatimedrivernative._
import emil.MailAddress
import emil.doobie.EmilDoobieMeta._
import zio._
import zio.interop.catz.asyncInstance

import java.time.OffsetDateTime

final case class DbDoobie(transactor: Transactor[Task]) extends Db {

  private implicit lazy val mailAddressRead: Read[MailAddress] = mailAddressMulticolumnRead
  private implicit lazy val mailAddressWrite: Write[MailAddress] = mailAddressMulticolumnWrite

  def createElection(
      metadata: ElectionMetadata,
      admin: Admin,
      options: List[BallotOption],
      voters: List[Voter],
  ): Task[Unit] = {
    val commands = for {
      electionId <- Update[ElectionMetadata](
        "INSERT INTO election (title, title_mangled, description, started) VALUES (?, ?, ?, ?)",
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

  def readElection(token: String): Task[Option[Election]] = {
    val commands = for {
      electionMetadataAndAdmin <-
        sql"""SELECT election.title, election.title_mangled, election.description, election.started, admin.name, admin.email, admin.token
              FROM voter
              JOIN election ON voter.election_id = election.id
              JOIN admin ON voter.election_id = admin.election_id
              WHERE voter.token = $token""".query[(ElectionMetadata, Admin)].option
      result <- electionMetadataAndAdmin match {
        case Some((electionMetadata, admin)) =>
          for {
            options <- // TODO: align indentation bellow `SELECT`
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
            endedAndResultId <-
              sql"""SELECT result.ended, result.id
              FROM voter
              JOIN result ON voter.election_id = result.election_id
              WHERE voter.token = $token""".query[(OffsetDateTime, Int)].option
            result <- endedAndResultId.traverse { case (ended, resultId) =>
              sql"""SELECT positions.option_id
              FROM positions
              WHERE positions.result_id = $resultId
              ORDER BY positions.ordering""".query[Int].to[List].map(Result(ended, _))
            }
            optionsMap = options.map(o => (o.id, o)).toMap
            votes <- votes0
              .groupBy(_._1)
              .valuesIterator
              .toList
              .traverse(preferences => Vote.makeValidated(preferences.map(_._2), optionsMap)) match {
              case Validated.Valid(vote) => Applicative[ConnectionIO].pure(vote)
              // TODO: ignores remaining errors
              case Validated.Invalid(e) => ApplicativeError[ConnectionIO, Throwable].raiseError(e.head)
            }
          } yield Option(Election(electionMetadata, admin, options, voters, votes, optionsMap, result))
        case None =>
          Applicative[ConnectionIO].pure[Option[Election]](None)
      }
    } yield result
    commands.transact(transactor)
  }

  def readElectionAdmin(token: String): Task[Option[Election]] = {
    val commands = for {
      electionMetadataAndAdmin <-
        sql"""SELECT election.title, election.title_mangled, election.description, election.started, admin.name, admin.email, admin.token
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
            endedAndResultId <-
              sql"""SELECT result.ended, result.id
              FROM admin
              JOIN result ON admin.election_id = result.election_id
              WHERE admin.token = $token""".query[(OffsetDateTime, Int)].option
            result <- endedAndResultId.traverse { case (ended, resultId) =>
              sql"""SELECT positions.option_id
              FROM positions
              WHERE positions.result_id = $resultId
              ORDER BY positions.ordering""".query[Int].to[List].map(Result(ended, _))
            }
            optionsMap = options.map(o => (o.id, o)).toMap
            votes <- votes0
              .groupBy(_._1)
              .valuesIterator
              .toList
              .traverse(preferences => Vote.makeValidated(preferences.map(_._2), optionsMap)) match {
              case Validated.Valid(vote) => Applicative[ConnectionIO].pure(vote)
              // TODO: ignores remaining errors
              case Validated.Invalid(e) => ApplicativeError[ConnectionIO, Throwable].raiseError(e.head)
            }
          } yield Option(
            Election(electionMetadata, admin, options, voters, votes, optionsMap, result),
          )
        case None =>
          Applicative[ConnectionIO].pure[Option[Election]](None)
      }
    } yield result
    commands.transact(transactor)
  }

  def castVote(token: String, vote: Vote): Task[VoteInsertResult] = {
    val commands = for {
      votedElectionidEnded <-
        sql"""SELECT voter.voted, voter.election_id, result.ended
              FROM voter
              LEFT JOIN result ON voter.election_id = result.election_id
              WHERE voter.token = $token""".query[(Boolean, Int, Option[OffsetDateTime])].option
      result <- votedElectionidEnded match {
        case None =>
          Applicative[ConnectionIO].pure[VoteInsertResult](VoteInsertResult.TokenNotFound)
        case Some((true, _, _)) =>
          Applicative[ConnectionIO].pure[VoteInsertResult](VoteInsertResult.AlreadyVoted)
        case Some((_, _, Some(_))) =>
          Applicative[ConnectionIO].pure[VoteInsertResult](VoteInsertResult.ElectionEnded)
        case Some((false, electionId, None)) =>
          for {
            _ <- sql"UPDATE voter SET voted = ${true} WHERE voter.token = $token".update.run
            voteId <- sql"INSERT INTO vote (election_id) VALUES ($electionId)".update
              .withUniqueGeneratedKeys[Int]("id")
            _ <- Update[(Int, Int, Int)](
              "INSERT INTO preference (vote_id, option_id, ordering) VALUES (?, ?, ?)",
            ).updateMany(vote.preferences.zipWithIndex.map { case (optionId, ordering) =>
              (voteId, optionId, ordering)
            })
          } yield VoteInsertResult.SuccessfullyVoted: VoteInsertResult
      }
    } yield result
    commands.transact(transactor)
  }

  def endElection(token: String, positions: List[Int], now: OffsetDateTime): Task[EndElectionResult] = {
    val commands = for {
      electionidEndedResultid <-
        sql"""SELECT admin.election_id, result.ended
              FROM admin
              LEFT JOIN result ON admin.election_id = result.election_id
              WHERE admin.token = $token""".query[(Int, Option[OffsetDateTime])].option
      result <- electionidEndedResultid match {
        case None =>
          Applicative[ConnectionIO].pure[EndElectionResult](EndElectionResult.TokenNotFound)
        case Some((_, Some(_))) =>
          Applicative[ConnectionIO].pure[EndElectionResult](EndElectionResult.ElectionAlreadyEnded)
        case Some((electionId, None)) =>
          for {
            resultId <- Update[(Int, OffsetDateTime)](
              "INSERT INTO result (election_id, ended) VALUES (?, ?)",
            ).withUniqueGeneratedKeys[Int]("id")((electionId, now))
            _ <- Update[(Int, Int, Int)](
              "INSERT INTO positions (result_id, option_id, ordering) VALUES (?, ?, ?)",
            ).updateMany(positions.zipWithIndex.map { case (optionId, ordering) => (resultId, optionId, ordering) })
          } yield EndElectionResult.SuccessfullyEnded: EndElectionResult
      }
    } yield result
    commands.transact(transactor)
  }

}

object DbDoobie {

  private[server] val layer = ZLayer.fromFunction(apply _).map(_.prune[Db])

  object Transactor {
    private[server] val layer = ZLayer.fromZIO(ZIO.service[server.Config].map(_.dbTransactor))
  }

}
