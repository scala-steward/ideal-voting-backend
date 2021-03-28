package cz.idealiste.idealvoting.server

import cats.implicits._
import com.dimafeng.testcontainers.DockerComposeContainer
import cz.idealiste.idealvoting
import cz.idealiste.idealvoting.server.Http._
import cz.idealiste.idealvoting.server.TestContainer.DockerCompose
import org.http4s._
import org.http4s.implicits._
import zio.blocking.Blocking
import zio._
import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestEnvironment
import zio.interop.catz._

object MainSpec extends DefaultRunnableSpec {

  private val makeApp: RManaged[Blocking with DockerCompose, HttpApp[EnvTask]] = for {
    dc <- Managed.service[DockerComposeContainer]
    url = show"jdbc:mysql://${dc.getServiceHost("mariadb", 3306)}:${dc.getServicePort("mariadb", 3306)}/idealvoting"
    db <- Db.make(Config.Db(url, "idealvoting", "idealvoting"))
    voting = new Voting(Config.Voting(), db)
    http = new idealvoting.server.Http(voting)
  } yield http.httpApp

  def spec: ZSpec[TestEnvironment, Failure] = {
    suite("Service")(
      testM("/status should return OK") {
        val response = makeApp.use(_.run(Request(method = Method.GET, uri = uri"/v1/status")))
        assertM(response.map(_.status))(equalTo(Status.Ok))
      },
      testM("/election POST should create an election") {
        val request = CreateElectionRequest(
          "election1",
          "admin1",
          List("option1", "option2"),
          List("voter1@x.com", "voter2@y.org"),
        )
        val response = makeApp.use { httpApp =>
          for {
            response <- httpApp.run(
              Request(method = Method.POST, uri = uri"/v1/election").withEntity(request),
            )
            response <- response.as[CreateElectionResponse]
            response <- httpApp.run(
              Request(method = Method.GET, uri = Uri.unsafeFromString(response.election))
                .withEntity(request),
            )
            response <- response.as[ElectionViewAdmin]
          } yield response
        }
        assertM(response)(
          equalTo(
            ElectionViewAdmin(
              "election1",
              "admin1",
              Map("voter1@x.com" -> ("voter1@x.com", false), "voter2@y.org" -> ("voter2@y.org", false)),
              Map(0 -> "option1", 1 -> "option2"),
            ),
          ),
        )
      },
    )
  }.provideCustomLayer(TestContainer.dockerCompose)
}
