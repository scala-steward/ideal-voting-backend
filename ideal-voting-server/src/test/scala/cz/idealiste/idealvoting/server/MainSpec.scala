package cz.idealiste.idealvoting.server

import cats.implicits.showInterpolator
import com.dimafeng.testcontainers.DockerComposeContainer
import cz.idealiste.idealvoting
import org.http4s._
import org.http4s.implicits._
import zio.Managed
import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestEnvironment

object MainSpec extends DefaultRunnableSpec {

  def spec: ZSpec[TestEnvironment, Failure] = {
    suite("Service")(
      testM("/status should return OK") {
        val db = for {
          dc <- Managed.service[DockerComposeContainer]
          url = show"jdbc:mysql://${dc.getServiceHost("mariadb", 3306)}:${dc.getServicePort("mariadb", 3306)}/idealvoting"
          db <- Db.make(Config.Db(url, "idealvoting", "idealvoting"))
        } yield db
        val response = db.use { db =>
          val voting = new Voting(Config.Voting(), db)
          val http = new idealvoting.server.Http(voting)
          http.httpApp.run(Request(method = Method.GET, uri = uri"/v1/status"))
        }
        assertM(response.map(_.status))(equalTo(Status.Ok))
      },
    )
  }.provideCustomLayer(TestContainer.dockerCompose)
}
