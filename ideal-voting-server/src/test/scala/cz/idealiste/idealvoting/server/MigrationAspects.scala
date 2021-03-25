package cz.idealiste.idealvoting.server

import cats.implicits.showInterpolator
import com.dimafeng.testcontainers.DockerComposeContainer
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.{Contexts, Liquibase}
import zio.blocking.{Blocking, effectBlocking}
import zio.test.TestAspect
import zio.test.TestAspect.before
import zio.{Has, ZIO}

import java.sql.DriverManager

object MigrationAspects {

  def migrate: TestAspect[Nothing, Blocking with Has[DockerComposeContainer], Nothing, Any] = {
    val migration = for {
      dc <- ZIO.service[DockerComposeContainer]
      _ <- effectBlocking(
        runMigration(
          show"jdbc:mysql://${dc.getServiceHost("mariadb", 3306)}:${dc.getServicePort("mariadb", 3306)}/test",
          "test",
          "test",
          "db/changelog/db.changelog-master.yaml",
        ),
      )
    } yield ()

    before(migration.orDie)
  }

  private def runMigration(
      url: String,
      username: String,
      password: String,
      changeLogFile: String,
  ): Unit = {
    val database = DatabaseFactory
      .getInstance()
      .findCorrectDatabaseImplementation(
        new JdbcConnection(DriverManager.getConnection(url, username, password)),
      )
    val liquibase = new Liquibase(
      changeLogFile,
      new ClassLoaderResourceAccessor(),
      database,
    )
    liquibase.update(new Contexts())
  }
}
