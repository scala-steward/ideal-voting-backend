package cz.idealiste.idealvoting.server

import cats.effect.Blocker
import cz.idealiste.idealvoting.server.Voting._
import doobie._
import doobie.hikari.HikariTransactor
import doobie.implicits._
import emil.doobie.EmilDoobieMeta._
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.{Contexts, Liquibase}
import zio._
import zio.blocking._
import zio.interop.catz._

import java.sql.Connection

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

  private def runMigration(connection: Connection, changeLogFile: String): Unit = {
    val database = DatabaseFactory
      .getInstance()
      .findCorrectDatabaseImplementation(new JdbcConnection(connection))
    val liquibase = new Liquibase(changeLogFile, new ClassLoaderResourceAccessor(), database)
    liquibase.update(new Contexts())
  }

  private def migrate(transactor: Transactor[Task], changeLogFile: String): RIO[Blocking, Unit] = {
    transactor.connect(transactor.kernel).toManagedZIO.use { connection =>
      effectBlocking {
        runMigration(connection, changeLogFile)
      }
    }
  }

  def make(config: Config.Db): RManaged[Blocking, Db] = Managed.runtime.flatMap { implicit r: Runtime[Any] =>
    for {
      ce <- ExecutionContexts.fixedThreadPool[Task](config.threadPoolSize).toManagedZIO
      be <- Blocker[Task].toManagedZIO
      transactor <- HikariTransactor
        .newHikariTransactor[Task](config.driverClassName, config.url, config.user, config.password, ce, be)
        .toManagedZIO
      _ <- migrate(transactor, config.changeLogFile).toManaged_
    } yield new Db(transactor)
  }
}
