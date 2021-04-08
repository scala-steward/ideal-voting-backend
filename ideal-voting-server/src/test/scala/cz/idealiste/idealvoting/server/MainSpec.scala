package cz.idealiste.idealvoting.server

import cz.idealiste.idealvoting.server.Http._
import emil.MailAddress
import emil.javamail.syntax._
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import org.http4s.implicits._
import org.http4s.{Method, Request, Status, Uri}
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.interop.catz._
import zio.random.Random
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._
import zio.test.environment.TestEnvironment

object MainSpec extends DefaultRunnableSpec {

  @SuppressWarnings(Array("DisableSyntax.throw"))
  private def email(string: String): MailAddress =
    MailAddress.parseValidated(string).fold(e => throw e.head, m => m)

  def spec: ZSpec[TestEnvironment, Failure] =
    suite("Service")(
      testM("/status should return OK") {
        val response =
          ZIO.service[Http].flatMap(_.httpApp.run(Request(method = Method.GET, uri = uri"/v1/status")))
        assertM(response.map(_.status))(equalTo(Status.Ok))
      },
      testM("/election POST should create an election") {
        val request = CreateElectionRequest(
          "election 1",
          None,
          email("Admin 1 <admin1@a.net>"),
          List(CreateOptionRequest("option1", None), CreateOptionRequest("option2", None)),
          List(email("Voter 1 <voter1@x.com>"), email("voter2@y.org")),
        )
        val response = ZIO.service[Http].flatMap { http =>
          val httpApp = http.httpApp
          for {
            response <- httpApp.run(
              Request(method = Method.POST, uri = uri"/v1/election").withEntity(request),
            )
            response <- response.as[CreateElectionResponse]
            response <- httpApp.run(
              Request(method = Method.GET, uri = Uri.unsafeFromString(response.election)),
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
            hasField(
              "admin.name",
              (r: GetElectionAdminResponse) => r.admin.name,
              equalTo(Option("Admin 1")),
            ) &&
            hasField(
              "admin.address",
              (r: GetElectionAdminResponse) => r.admin.address,
              equalTo("admin1@a.net"),
            ) &&
            hasField(
              "options",
              (r: GetElectionAdminResponse) => r.options,
              equalTo(List(GetOptionResponse(0, "option1", None), GetOptionResponse(1, "option2", None))),
            ) &&
            hasField(
              "voters",
              (r: GetElectionAdminResponse) => r.voters.map(r => (r.voter.name, r.voter.address, r.voted)),
              equalTo(
                List(
                  (Some("Voter 1"), "voter1@x.com", false),
                  (None, "voter2@y.org", false),
                ),
              ),
            ),
        )
      },
    ).provideSomeLayerShared[Blocking with Random](testLayer.orDie) @@ sequential

  lazy val testLayer: RLayer[Blocking with Random, Has[Http]] = (
    ZLayer.identity[Blocking] ++
      Clock.live ++
      ZLayer.identity[Random] ++ (
        (Config.layer ++ (ZLayer.identity[Blocking] >>> TestContainer.dockerCompose)) >>> TestContainer.config
      )
  ) >>> Main.httpLayer
}
