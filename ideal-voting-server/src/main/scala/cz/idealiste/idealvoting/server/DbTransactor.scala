package cz.idealiste.idealvoting.server

import cats.effect.Blocker
import doobie.hikari.HikariTransactor
import doobie.{ExecutionContexts, Transactor}
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.{Contexts, Liquibase}
import zio._
import zio.blocking.{Blocking, effectBlocking}
import zio.interop.catz._

import java.sql.Connection

object DbTransactor {

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

  def make(config: Config.DbTransactor): RManaged[Blocking, Transactor[Task]] = Managed.runtime.flatMap {
    implicit r: Runtime[Any] =>
      for {
        ce <- ExecutionContexts.fixedThreadPool[Task](config.threadPoolSize).toManagedZIO
        be <- Blocker[Task].toManagedZIO
        transactor <- HikariTransactor
          .newHikariTransactor[Task](config.driverClassName, config.url, config.user, config.password, ce, be)
          .toManagedZIO
        _ <- migrate(transactor, config.changeLogFile).toManaged_
      } yield transactor
  }

  val layer: RLayer[Blocking with Has[Config.DbTransactor], Has[Transactor[Task]]] =
    ZLayer.fromServiceManaged[Config.DbTransactor, Blocking, Throwable, Transactor[Task]](make)

}
