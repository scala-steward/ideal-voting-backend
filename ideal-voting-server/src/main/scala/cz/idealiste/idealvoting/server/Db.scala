package cz.idealiste.idealvoting.server

import cz.idealiste.idealvoting.server.Voting._
import doobie._
import doobie.implicits._
import emil.doobie.EmilDoobieMeta._
import zio._
import zio.interop.catz._

class Db(transactor: Transactor[Task]) {

  def createElection(election: Election): Task[Unit] = {
    val commands = for {
      electionId <- Update[ElectionMetadata](
        "INSERT INTO election (title, title_mangled, description) VALUES (?, ?, ?)",
      ).withUniqueGeneratedKeys[Int]("id")(election.metadata)
      _ <- Update[(Int, Admin)](
        "INSERT INTO admin (election_id, name, email, token) VALUES (?, ?, ?, ?)",
      ).run((electionId, election.admin))
      _ <- Update[(Int, BallotOption)](
        "INSERT INTO option (election_id, option_id, title, description) VALUES (?, ?, ?, ?)",
      ).updateMany(election.options.map((electionId, _)))
      _ <- Update[(Int, Voter)](
        "INSERT INTO voter (election_id, name, email, token, voted) VALUES (?, ?, ?, ?, ?)",
      ).updateMany(election.voters.map((electionId, _)))
    } yield ()
    commands.transact(transactor)
  }

  def readElection(token: String): Task[Election] = {
    val commands = for {
      (electionMetadata, admin) <-
        sql"""SELECT election.title, election.title_mangled, election.description, admin.name, admin.email, admin.token
              FROM voter
              JOIN election ON voter.election_id = election.id
              JOIN admin ON voter.election_id = admin.election_id
              WHERE voter.token = $token""".query[(ElectionMetadata, Admin)].unique
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
    } yield Election(electionMetadata, admin, options, voters)
    commands.transact(transactor)
  }

  def readElectionAdmin(token: String): Task[Election] = {
    val commands = for {
      (electionMetadata, admin) <-
        sql"""SELECT election.title, election.title_mangled, election.description, admin.name, admin.email, admin.token
              FROM admin
              JOIN election ON admin.election_id = election.id
              WHERE admin.token = $token""".query[(ElectionMetadata, Admin)].unique
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
    } yield Election(electionMetadata, admin, options, voters)
    commands.transact(transactor)
  }

}

object Db {

  def make(transactor: Transactor[Task]): Db = new Db(transactor)

  val layer: URLayer[Has[Transactor[Task]], Has[Db]] =
    ZLayer.fromService[Transactor[Task], Db](new Db(_))

}
