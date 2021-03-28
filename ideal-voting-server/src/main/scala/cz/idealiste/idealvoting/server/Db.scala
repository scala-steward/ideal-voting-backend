package cz.idealiste.idealvoting.server

import cats.effect.Blocker
import cz.idealiste.idealvoting.server.Http._
import doobie._
import doobie.hikari.HikariTransactor
import doobie.implicits._
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.{Contexts, Liquibase}
import zio._
import zio.blocking._
import zio.interop.catz._

import java.sql.Connection

class Db(transactor: Transactor[Task]) {

  def createElection(
      name: String,
      admin: (String, String),
      options: List[(String, Int)],
      voters: List[(String, String, String, Boolean)],
  ): Task[Unit] = {
    val commands = for {
      electionId <- sql"INSERT INTO election (name) VALUES ($name)".update.withUniqueGeneratedKeys[Int]("id")
      _ <-
        sql"INSERT INTO admin (election_id, admin, token) VALUES ($electionId, ${admin._1}, ${admin._2})".update.run
      _ <- Update[(Int, String, Int)](
        "INSERT INTO option (election_id, option, option_id) VALUES (?, ?, ?)",
      ).updateMany(options.map { case (option, optionId) => (electionId, option, optionId) })
      _ <- Update[(Int, String, String, String, Boolean)](
        "INSERT INTO voter (election_id, voter_fullname, voter, token, voted) VALUES (?, ?, ?, ?, ?)",
      ).updateMany(voters.map { case (voterFullname, voter, token, voted) =>
        (electionId, voterFullname, voter, token, voted)
      })
    } yield ()
    commands.transact(transactor)
  }

  def viewElection(token: String): Task[ElectionView] = {
    val commands = for {
      (name, admin, voterFullname, voter, voted) <-
        sql"""SELECT election.name, admin.admin, voter.voter_fullname, voter.voter, voter.voted
              FROM voter
              JOIN election ON election.id = voter.election_id
              JOIN admin ON election.id = admin.election_id
              WHERE voter.token = $token""".query[(String, String, String, String, Boolean)].unique
      options <-
        sql"""SELECT option.option_id, option.option
              FROM voter
              JOIN option ON option.id = voter.election_id
              WHERE voter.token = $token""".query[(Int, String)].to[List]
      optionsMap = options.toMap
    } yield ElectionView(name, admin, voterFullname, voter, voted, optionsMap)
    commands.transact(transactor)
  }

  def viewElectionAdmin(token: String): Task[ElectionViewAdmin] = {
    val commands = for {
      (name, admin) <-
        sql"""SELECT election.name, admin.admin
              FROM admin
              JOIN election ON election.id = admin.election_id
              WHERE admin.token = $token""".query[(String, String)].unique
      options <-
        sql"""SELECT option.option_id, option.option
              FROM admin
              JOIN option ON option.election_id = admin.election_id
              WHERE admin.token = $token""".query[(Int, String)].to[List]
      voters <-
        sql"""SELECT voter.voter, voter.voter_fullname, voter.voted
              FROM admin
              JOIN voter ON voter.election_id = admin.election_id
              WHERE admin.token = $token""".query[(String, (String, Boolean))].to[List]
      optionsMap = options.toMap
      votersMap = voters.toMap
    } yield ElectionViewAdmin(name, admin, votersMap, optionsMap)
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
