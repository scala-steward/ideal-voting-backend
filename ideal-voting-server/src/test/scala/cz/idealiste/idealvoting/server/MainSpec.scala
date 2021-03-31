package cz.idealiste.idealvoting.server

import cats.implicits._
import com.dimafeng.testcontainers.DockerComposeContainer
import cz.idealiste.idealvoting.server.Http._
import cz.idealiste.idealvoting.server.TestContainer.DockerCompose
import org.http4s
import org.http4s.implicits._
import org.http4s.{Method, Request, Uri}
import zio._
import zio.blocking.Blocking
import zio.interop.catz._
import zio.random.Random
import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestEnvironment

object MainSpec extends DefaultRunnableSpec {

  private val makeApp: RManaged[Blocking with DockerCompose with Random, http4s.HttpApp[Task]] = for {
    dc <- Managed.service[DockerComposeContainer]
    url = show"jdbc:mysql://${dc.getServiceHost("mariadb", 3306)}:${dc.getServicePort("mariadb", 3306)}/idealvoting"
    dbTransactor <- DbTransactor.make(Config.DbTransactor(url, "idealvoting", "idealvoting"))
    db = Db.make(dbTransactor)
    random <- Managed.access[Random](_.get)
    voting = Voting.make(Config.Voting(), db, random)
    http = Http.make(voting)
  } yield http.httpApp

  def spec: ZSpec[TestEnvironment, Failure] = {
    suite("Service")(
//      testM("/status should return OK") {
//        val response = makeApp.use(_.run(Request(method = Method.GET, uri = uri"/v1/status")))
//        assertM(response.map(_.status))(equalTo(Status.Ok))
//      },
      testM("/election POST should create an election") {
        val request = CreateElectionRequest(
          "election 1",
          None,
          "admin1@a.net",
          List(CreateOptionRequest("option1", None), CreateOptionRequest("option2", None)),
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
            response <- response.as[GetElectionAdminResponse]
          } yield response
        }
        assertM(response)(
          hasField("title", (r: GetElectionAdminResponse) => r.title, equalTo("election 1")) &&
            hasField(
              "titleMangled",
              (r: GetElectionAdminResponse) => r.titleMangled,
              equalTo("election-1"),
            ) &&
            hasField(
              "description",
              (r: GetElectionAdminResponse) => r.description,
              equalTo(None: Option[String]),
            ) &&
            hasField("admin", (r: GetElectionAdminResponse) => r.admin, equalTo("admin1@a.net")) &&
            hasField(
              "options",
              (r: GetElectionAdminResponse) => r.options,
              equalTo(List(GetOptionResponse(0, "option1", None), GetOptionResponse(1, "option2", None))),
            ) &&
            hasField(
              "voters",
              (r: GetElectionAdminResponse) => r.voters,
              equalTo(
                List(
                  GetVoterResponse("voter1@x.com", voted = false),
                  GetVoterResponse("voter2@y.org", voted = false),
                ),
              ),
            ),
        )
      },
    )
  }.provideCustomLayer(TestContainer.dockerCompose)
}
