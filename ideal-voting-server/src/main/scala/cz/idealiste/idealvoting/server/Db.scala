package cz.idealiste.idealvoting.server

import cats.effect.Blocker
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
  def createElections(
      name: String,
      admin: (String, String),
      options: List[String],
      voters: List[(String, String)],
  ): Task[Unit] = {

    val commands = for {
      electionId <- sql"INSERT INTO election (name) VALUES ($name)".update.withUniqueGeneratedKeys[Int]("id")
      _ <-
        sql"INSERT INTO election (election_id, admin, token) VALUES ($electionId, ${admin._1}, ${admin._2})".update.run
      _ <- Update[(Int, String)]("INSERT INTO option (election_id, option) VALUES (?, ?)")
        .updateMany(options.map((electionId, _)))
      _ <- Update[(Int, String, String)]("INSERT INTO voter (election_id, voter, token) VALUES (?, ?, ?)")
        .updateMany(voters.map { case (voter, token) => (electionId, voter, token) })
    } yield ()

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
      _ <- Managed.fromEffect(migrate(transactor, config.changeLogFile))
    } yield new Db(transactor)
  }

}
