package cz.idealiste.idealvoting.server

import cats.data.ValidatedNec
import cats.implicits.*
import cz.idealiste.idealvoting.contract
import cz.idealiste.idealvoting.contract.definitions.LinksResponse.Links
import cz.idealiste.idealvoting.server.HandlerLive.*
import cz.idealiste.idealvoting.server.Voting.*
import emil.MailAddress
import emil.javamail.syntax.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.scalaland.chimney.cats.*
import io.scalaland.chimney.dsl.*
import io.scalaland.chimney.{TransformationError, Transformer, TransformerF}
import org.http4s.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.Http4sDsl
import zio.*
import zio.interop.catz.asyncInstance

import java.time.OffsetDateTime

final case class HandlerLive(voting: Voting, clock: Clock) extends Handler {

  override def createElection(respond: contract.Resource.CreateElectionResponse.type)(
      body: contract.definitions.CreateElectionRequest,
      xCorrelationId: String,
  ): Task[contract.Resource.CreateElectionResponse] =
    body.validateAs[CreateElection] match {
      case Right(body) =>
        for {
          now <- clock.currentDateTime
          (titleMangled, token) <- voting.createElection(body, now)
          links = Vector(
            Links(
              show"/api/v1/election/admin/$titleMangled/$token",
              "getElectionAdmin",
              Links.Method.Get,
              Vector(Links.Parameters("titleMangled", titleMangled), Links.Parameters("token", token)),
            ),
          )
          resp = contract.definitions.LinksResponse(links)
        } yield respond.Created(resp)
      case Left(value) =>
        ZIO.succeed(respond.BadRequest(contract.definitions.BadRequestResponse(value)))
    }

  override def getElectionAdmin(
      respond: contract.Resource.GetElectionAdminResponse.type,
  )(titleMangled: String, token: String, xCorrelationId: String): Task[contract.Resource.GetElectionAdminResponse] =
    for {
      electionViewAdmin <- voting.viewElectionAdmin(token)
      resp = electionViewAdmin.map { electionViewAdmin =>
        val titleMangled = electionViewAdmin.metadata.titleMangled
        val links = Vector(
          Links(
            show"/api/v1/election/admin/$titleMangled/$token",
            "self",
            Links.Method.Get,
            Vector(Links.Parameters("titleMangled", titleMangled), Links.Parameters("token", token)),
          ),
        ) ++ (
          if (electionViewAdmin.result.isDefined) Vector()
          else
            Vector(
              Links(
                show"/api/v1/election/admin/$titleMangled/$token",
                "election-end",
                Links.Method.Post,
                Vector(Links.Parameters("titleMangled", titleMangled), Links.Parameters("token", token)),
              ),
            )
        )
        contract.definitions.GetElectionAdminResponse(
          electionViewAdmin.metadata.title,
          titleMangled,
          electionViewAdmin.metadata.description,
          electionViewAdmin.metadata.started,
          electionViewAdmin.admin.email.transformInto,
          electionViewAdmin.admin.token,
          electionViewAdmin.options.transformInto[Vector[contract.definitions.GetOptionResponse]],
          electionViewAdmin.voters.transformInto[Vector[contract.definitions.GetVoterResponse]],
          electionViewAdmin.result.transformInto[Option[contract.definitions.GetResultResponse]],
          contract.definitions.LinksResponse(links),
        )
      }
    } yield resp match {
      case Some(resp) => respond.Ok(resp)
      case None       => respond.NotFound(contract.definitions.NotFoundResponse("Election not found."))
    }

  val oldRoutes: HttpRoutes[Task] = {
    val Http4sDslTask = Http4sDsl[Task]
    import Http4sDslTask.*

    HttpRoutes.of[Task] {
      case GET -> Root / "status" =>
        Ok("OK")
      case req @ POST -> Root / "election" =>
        for {
          req <- req.as[CreateElectionRequest]
          createElection = CreateElection(
            req.title,
            req.description,
            req.admin,
            req.options.map(req => CreateOption(req.title, req.description)),
            req.voters,
          )
          now <- clock.currentDateTime
          (titleMangled, token) <- voting.createElection(createElection, now)
          resp = LinksResponse(
            List(
              Link(
                show"/v1/election/admin/$titleMangled/$token",
                "election-view-admin",
                GET,
                Map("titleMangled" -> titleMangled, "token" -> token),
              ),
            ),
          )
          resp <- Created(resp)
        } yield resp
      case GET -> Root / "election" / _ / token =>
        for {
          electionView <- voting.viewElection(token)
          resp = electionView.map { electionView =>
            val titleMangled = electionView.metadata.titleMangled
            GetElectionResponse(
              electionView.metadata.title,
              electionView.metadata.titleMangled,
              electionView.metadata.description,
              electionView.metadata.started,
              electionView.admin.email,
              electionView.options.map(o => GetOptionResponse(o.id, o.title, o.description)),
              electionView.voter.email,
              electionView.voter.token,
              electionView.result.map(r =>
                GetResultResponse(r.result.ended, r.result.positions, r.votes.map(_.preferences)),
              ),
              List(
                Link(
                  show"/v1/election/$titleMangled/$token",
                  "self",
                  GET,
                  Map("titleMangled" -> titleMangled, "token" -> token),
                ),
              ) ++ (
                if (electionView.voter.voted || electionView.result.isDefined) List()
                else
                  List(
                    Link(
                      show"/v1/election/$titleMangled/$token",
                      "cast-vote",
                      POST,
                      Map("titleMangled" -> titleMangled, "token" -> token),
                    ),
                  )
              ),
            )
          }
          resp <- resp match {
            case Some(resp) => Ok(resp)
            case None       => NotFound(Error("Election not found."))
          }
        } yield resp
      case req @ POST -> Root / "election" / titleMangled / token =>
        for {
          req <- req.as[CastVoteRequest]
          result <- voting.castVote(token, req.preferences)
          resp <- result match {
            case InvalidVote.DuplicateOptions(_) =>
              BadRequest(Error("Vote is invalid, contains duplicate options."))
            case InvalidVote.UnavailableOptions(_) =>
              BadRequest(Error("Vote is invalid, contains unavailable options."))
            case VoteInsertResult.AlreadyVoted =>
              Conflict(Error("Voter has already voted."))
            case VoteInsertResult.ElectionEnded =>
              Conflict(Error("Election has already ended."))
            case VoteInsertResult.TokenNotFound =>
              NotFound(Error("Election not found."))
            case VoteInsertResult.SuccessfullyVoted =>
              val resp = LinksResponse(
                List(
                  Link(
                    show"/v1/election/$titleMangled/$token",
                    "election-view",
                    GET,
                    Map("titleMangled" -> titleMangled, "token" -> token),
                  ),
                ),
              )
              Accepted(resp)
          }
        } yield resp
      case GET -> Root / "election" / "admin" / _ / token =>
        for {
          electionViewAdmin <- voting.viewElectionAdmin(token)
          resp = electionViewAdmin.map { electionViewAdmin =>
            val titleMangled = electionViewAdmin.metadata.titleMangled
            GetElectionAdminResponse(
              electionViewAdmin.metadata.title,
              titleMangled,
              electionViewAdmin.metadata.description,
              electionViewAdmin.metadata.started,
              electionViewAdmin.admin.email,
              electionViewAdmin.admin.token,
              electionViewAdmin.options.map(o => GetOptionResponse(o.id, o.title, o.description)),
              electionViewAdmin.voters.map(v => GetVoterResponse(v.email, v.voted)),
              electionViewAdmin.result.map(r =>
                GetResultResponse(r.result.ended, r.result.positions, r.votes.map(_.preferences)),
              ),
              List(
                Link(
                  show"/v1/election/admin/$titleMangled/$token",
                  "self",
                  GET,
                  Map("titleMangled" -> titleMangled, "token" -> token),
                ),
              ) ++ (
                if (electionViewAdmin.result.isDefined) List()
                else
                  List(
                    Link(
                      show"/v1/election/admin/$titleMangled/$token",
                      "election-end",
                      POST,
                      Map("titleMangled" -> titleMangled, "token" -> token),
                    ),
                  )
              ),
            )
          }
          resp <- resp match {
            case Some(resp) => Ok(resp)
            case None       => NotFound(Error("Election not found."))
          }
        } yield resp
      case POST -> Root / "election" / "admin" / titleMangled / token =>
        for {
          now <- clock.currentDateTime
          result <- voting.endElection(token, now)
          resp <- result match {
            case EndElectionResult.TokenNotFound =>
              NotFound(Error("Election not found."))
            case EndElectionResult.ElectionAlreadyEnded =>
              Conflict(Error("Election has already been ended."))
            case EndElectionResult.SuccessfullyEnded =>
              val resp = LinksResponse(
                List(
                  Link(
                    show"/v1/election/admin/$titleMangled/$token",
                    "election-view-admin",
                    GET,
                    Map("titleMangled" -> titleMangled, "token" -> token),
                  ),
                ),
              )
              Accepted(resp)
          }
        } yield resp

    }
  }

}

object HandlerLive {

  type Validation[+A] = ValidatedNec[TransformationError[String], A]

  implicit class ValidationOps[A](private val self: A) extends AnyVal {
    def validateAs[B](implicit ev: TransformerF[Validation[+*], A, B]): Either[String, B] =
      self.transformIntoF[Validation[+*], B].leftMap(_.iterator.mkString("\n")).toEither
  }

  implicit lazy val validationMailAddress: TransformerF[Validation[+*], String, MailAddress] =
    string => MailAddress.parseValidated(string).leftMap(_.map(e => TransformationError(e.getMessage)))

  implicit lazy val encodingMailAddress: Transformer[MailAddress, String] =
    _.asUnicodeString

  implicit lazy val encodingInt: Transformer[Int, BigDecimal] =
    BigDecimal.int2bigDecimal(_)

  implicit lazy val encodingResultView: Transformer[ResultView, contract.definitions.GetResultResponse] =
    Transformer
      .define[ResultView, contract.definitions.GetResultResponse]
      .withFieldComputed(_.ended, _.result.ended)
      .withFieldComputed(_.positions, _.result.positions.transformInto[Vector[BigDecimal]])
//      .withFieldComputed(_.votes, _.votes.transformInto[Vector[contract.definitions.GetResultResponse.Votes]])
      .buildTransformer

  implicit lazy val emailDecoder: Decoder[MailAddress] = {
    final case class MailAddressStructure(name: Option[String], address: String)
    Decoder[String]
      .emap(MailAddress.parse)
      .or(deriveDecoder[MailAddressStructure].emap(s => MailAddress.parseAddressAndName(s.name, s.address)))
  }
  implicit lazy val emailEncoder: Encoder[MailAddress] = deriveEncoder

  implicit lazy val methodDecoder: Decoder[Method] =
    Decoder[String].emap(Method.fromString(_).leftMap(_.sanitized))
  implicit lazy val methodEncoder: Encoder[Method] = Encoder[String].contramap(_.name)

  final case class Link(href: String, rel: String, method: Method, parameters: Map[String, String])

  object Link {
    implicit lazy val decoder: Decoder[Link] = deriveDecoder
    implicit lazy val encoder: Encoder[Link] = deriveEncoder
  }

  final case class CreateOptionRequest(title: String, description: Option[String])

  object CreateOptionRequest {
    implicit lazy val decoder: Decoder[CreateOptionRequest] = deriveDecoder
    implicit lazy val encoder: Encoder[CreateOptionRequest] = deriveEncoder
  }

  final case class CreateElectionRequest(
      title: String,
      description: Option[String],
      admin: MailAddress,
      options: List[CreateOptionRequest],
      voters: List[MailAddress],
  )

  object CreateElectionRequest {
    implicit lazy val decoder: Decoder[CreateElectionRequest] = deriveDecoder
    implicit lazy val encoder: Encoder[CreateElectionRequest] = deriveEncoder
  }

  final case class LinksResponse(links: List[Link])

  object LinksResponse {
    implicit lazy val encoder: Encoder[LinksResponse] = deriveEncoder
    implicit lazy val decoder: Decoder[LinksResponse] = deriveDecoder
  }

  final case class GetOptionResponse(id: Int, title: String, description: Option[String])

  object GetOptionResponse {
    implicit lazy val encoder: Encoder[GetOptionResponse] = deriveEncoder
    implicit lazy val decoder: Decoder[GetOptionResponse] = deriveDecoder
  }

  final case class GetElectionResponse(
      title: String,
      titleMangled: String,
      description: Option[String],
      started: OffsetDateTime,
      admin: MailAddress,
      options: List[GetOptionResponse],
      voter: MailAddress,
      voterToken: String,
      result: Option[GetResultResponse],
      links: List[Link],
  )

  object GetElectionResponse {
    implicit lazy val encoder: Encoder[GetElectionResponse] = deriveEncoder
    implicit lazy val decoder: Decoder[GetElectionResponse] = deriveDecoder
  }

  final case class GetVoterResponse(voter: MailAddress, voted: Boolean)

  object GetVoterResponse {
    implicit lazy val encoder: Encoder[GetVoterResponse] = deriveEncoder
    implicit lazy val decoder: Decoder[GetVoterResponse] = deriveDecoder
  }

  final case class GetResultResponse(ended: OffsetDateTime, positions: List[Int], votes: List[List[Int]])

  object GetResultResponse {
    implicit lazy val encoder: Encoder[GetResultResponse] = deriveEncoder
    implicit lazy val decoder: Decoder[GetResultResponse] = deriveDecoder
  }

  final case class GetElectionAdminResponse(
      title: String,
      titleMangled: String,
      description: Option[String],
      started: OffsetDateTime,
      admin: MailAddress,
      adminToken: String,
      options: List[GetOptionResponse],
      voters: List[GetVoterResponse],
      result: Option[GetResultResponse],
      links: List[Link],
  )

  object GetElectionAdminResponse {
    implicit lazy val encoder: Encoder[GetElectionAdminResponse] = deriveEncoder
    implicit lazy val decoder: Decoder[GetElectionAdminResponse] = deriveDecoder
  }

  final case class CastVoteRequest(preferences: List[Int])

  object CastVoteRequest {
    implicit lazy val encoder: Encoder[CastVoteRequest] = deriveEncoder
    implicit lazy val decoder: Decoder[CastVoteRequest] = deriveDecoder
  }

  final case class Error(error: String)
  object Error {
    implicit lazy val encoder: Encoder[Error] = deriveEncoder
    implicit lazy val decoder: Decoder[Error] = deriveDecoder
  }

  private[server] val layer = ZLayer.fromZIO {
    for {
      clock <- ZIO.service[Clock]
      voting <- ZIO.service[Voting]
      handler: Handler = HandlerLive(voting, clock)
    } yield handler
  }

}
