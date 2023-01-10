package cz.idealiste.idealvoting.server

import cats.implicits.*
import com.dimafeng.testcontainers.DockerComposeContainer
import cz.idealiste.idealvoting.server.HandlerLive.*
import emil.MailAddress
import emil.javamail.syntax.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.implicits.*
import org.http4s.{Method, Request, Status, Uri}
import zio.*
import zio.interop.catz.*
import zio.logging.backend.SLF4J
import zio.test.Assertion.*
import zio.test.TestAspect.*
import zio.test.*

object MainSpec extends ZIOSpecDefault {

  @SuppressWarnings(Array("DisableSyntax.throw"))
  private def email(string: String): MailAddress =
    MailAddress.parseValidated(string).valueOr(e => throw e.head)

  def spec: Spec[TestEnvironment, Any] =
    suite("Service")(
      test("/status should return OK") {
        val response =
          ZIO.serviceWithZIO[HttpApp](_.httpApp.run(Request(method = Method.GET, uri = uri"/v1/status")))
        assertZIO(response.map(_.status))(equalTo(Status.Ok))
      },
      test("/election POST should create an election") {
        val request = CreateElectionRequest(
          "election 1",
          None,
          email("Admin 1 <admin1@a.net>"),
          List(CreateOptionRequest("option1", None), CreateOptionRequest("option2", Some("Option 2"))),
          List(email("Voter 1 <voter1@x.com>"), email("voter2@y.org")),
        )
        val response = ZIO.serviceWithZIO[HttpApp] { http =>
          val httpApp = http.httpApp
          for {
            response <- httpApp.run(
              Request(method = Method.POST, uri = uri"/v1/election").withEntity(request),
            )
            response <- response.as[LinksResponse]
            response <- httpApp.run(
              Request(
                method = Method.GET,
                uri = Uri.unsafeFromString(
                  response.links
                    .find(l => l.method === Method.GET && l.rel === "election-view-admin")
                    .get
                    .href,
                ),
              ),
            )
            response <- response.as[GetElectionAdminResponse]
          } yield response
        }
        assertZIO(response)(
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
              equalTo(
                List(GetOptionResponse(0, "option1", None), GetOptionResponse(1, "option2", Some("Option 2"))),
              ),
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
      test("/election/.../<token> POST should cast a vote") {
        val requestCreate = CreateElectionRequest(
          "election 2",
          None,
          email("Admin 1 <admin1@a.net>"),
          List(CreateOptionRequest("option1", None), CreateOptionRequest("option2", None)),
          List(email("Voter 1 <voter1@x.com>"), email("voter2@y.org")),
        )
        val requestCast = CastVoteRequest(List(1, 0))
        val responseViewAdmin = ZIO.serviceWithZIO[HttpApp] { http =>
          val httpApp = http.httpApp
          for {
            responseCreate <- httpApp
              .run(
                Request(method = Method.POST, uri = uri"/v1/election").withEntity(requestCreate),
              )
              .flatMap(_.as[LinksResponse])
            _ <- httpApp
              .run(
                Request(
                  method = Method.POST,
                  uri = Uri.unsafeFromString("/v1/election/uri-mangled-xyz/tvehbtrszd"),
                ).withEntity(requestCast),
              )
              .flatMap(_.as[LinksResponse])
            responseViewAdmin <- httpApp
              .run(
                Request(
                  method = Method.GET,
                  uri = Uri.unsafeFromString(
                    responseCreate.links
                      .find(l => l.method === Method.GET && l.rel === "election-view-admin")
                      .get
                      .href,
                  ),
                ),
              )
              .flatMap(_.as[GetElectionAdminResponse])
          } yield responseViewAdmin
        }
        assertZIO(responseViewAdmin)(
          hasField(
            "voters",
            (r: GetElectionAdminResponse) => r.voters.map(r => (r.voter.name, r.voter.address, r.voted)),
            equalTo(
              List(
                (Some("Voter 1"), "voter1@x.com", true),
                (None, "voter2@y.org", false),
              ),
            ),
          ),
        )
      },
      test("/election/admin/.../<token> POST should end the election") {
        val requestCreate = CreateElectionRequest(
          "election 3",
          None,
          email("Admin 1 <admin1@a.net>"),
          List(CreateOptionRequest("option1", None), CreateOptionRequest("option2", None)),
          List(email("Voter 1 <voter1@x.com>"), email("voter2@y.org")),
        )
        val requestCast = CastVoteRequest(List(1, 0))
        val responseResult = ZIO.serviceWithZIO[HttpApp] { http =>
          val httpApp = http.httpApp
          for {
            responseCreate <- httpApp
              .run(Request(method = Method.POST, uri = uri"/v1/election").withEntity(requestCreate))
              .flatMap(_.as[LinksResponse])
            _ <- httpApp
              .run(
                Request(method = Method.POST, uri = Uri.unsafeFromString("/v1/election/uri-mangled-xyz/tkpfinzdlw"))
                  .withEntity(requestCast),
              )
              .flatMap(_.as[LinksResponse])
            _ <- httpApp
              .run(
                Request(
                  method = Method.POST,
                  uri = Uri.unsafeFromString(
                    s"/v1/election/admin/uri-mangled-xyz/${responseCreate.links(0).parameters("token")}",
                  ),
                ),
              )
              .flatMap(_.as[LinksResponse])
            responseViewAdmin <- httpApp
              .run(
                Request(
                  method = Method.GET,
                  uri = Uri.unsafeFromString(
                    responseCreate.links.find(l => l.method === Method.GET && l.rel === "election-view-admin").get.href,
                  ),
                ),
              )
              .flatMap(_.as[GetElectionAdminResponse])
          } yield responseViewAdmin.result.map(r => (r.positions, r.votes))
        }
        assertZIO(responseResult)(equalTo(Some((List(1, 0), List(List(1, 0))))))
      },
    ).provideShared(testLayer.orDie) @@ sequential

  lazy val testLayerConfig: TaskLayer[Config] =
    ZLayer.make[Config & DockerComposeContainer](
      Runtime.removeDefaultLoggers,
      SLF4J.slf4j,
      ZIOAppArgs.empty,
      Config.layer,
      TestContainer.dockerCompose,
    ) >>> TestContainer.layer

  lazy val testLayer: TaskLayer[HttpApp] =
    ZLayer.make[HttpApp](
      liveEnvironment.map(_.prune[Clock]),
      TestRandom.deterministic,
      testLayerConfig,
      Main.httpLayer,
    )

}
