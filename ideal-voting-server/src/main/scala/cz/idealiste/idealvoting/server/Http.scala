package cz.idealiste.idealvoting.server

import cats.implicits._
import cz.idealiste.idealvoting.server.Http._
import cz.idealiste.idealvoting.server.Voting._
import emil.MailAddress
import emil.javamail.syntax._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.http4s._
import org.http4s.circe.CirceEntityDecoder.circeEntityDecoder
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.Router
import zio._
import zio.clock.Clock
import zio.interop.catz._

class Http(voting: Voting, clock: Clock.Service) {

  private val serviceV1 = {
    val Http4sDslTask: Http4sDsl[Task] = Http4sDsl[Task]
    import Http4sDslTask._

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
              Link(show"/v1/election/admin/$titleMangled/$token", "election-view-admin", GET),
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
              electionView.admin.email,
              electionView.options.map(o => GetOptionResponse(o.id, o.title, o.description)),
              electionView.voter.email,
              electionView.voter.token,
              List(Link(show"/v1/election/$titleMangled/$token", "self", GET)) ++ (
                if (electionView.voter.voted) List()
                else
                  List(
                    Link(show"/v1/election/$titleMangled/$token", "cast-vote", POST),
                  )
              ),
            )
          }
          resp <- resp match {
            case Some(resp) => Ok(resp)
            case None       => NotFound()
          }
        } yield resp
      case req @ POST -> Root / "election" / titleMangled / token =>
        for {
          req <- req.as[CastVoteRequest]
          result <- voting.castVote(token, req.preferences)
          resp <- result match {
            case invalidVote: InvalidVote       => BadRequest(invalidVote.message)
            case VoteInsertResult.AlreadyVoted  => Conflict()
            case VoteInsertResult.TokenNotFound => NotFound()
            case VoteInsertResult.SuccessfullyVoted =>
              val resp = LinksResponse(
                List(
                  Link(show"/v1/election/$titleMangled/$token", "election-view", GET),
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
              electionViewAdmin.admin.email,
              electionViewAdmin.admin.token,
              electionViewAdmin.options.map(o => GetOptionResponse(o.id, o.title, o.description)),
              electionViewAdmin.voters.map(v => GetVoterResponse(v.email, v.voted)),
              List(
                Link(show"/v1/election/admin/$titleMangled/$token", "self", GET),
              ),
            )
          }
          resp <- resp match {
            case Some(resp) => Ok(resp)
            case None       => NotFound()
          }
        } yield resp

    }
  }

  val httpApp: HttpApp[Task] =
    Router("/v1" -> serviceV1).orNotFound

}

object Http {

  implicit lazy val emailDecoder: Decoder[MailAddress] = {
    final case class MailAddressStructure(name: Option[String], address: String)
    Decoder[String]
      .emap(MailAddress.parse)
      .or(deriveDecoder[MailAddressStructure].emap(s => MailAddress.parseAddressAndName(s.name, s.address)))
  }
  implicit lazy val emailEntityDecoder: EntityDecoder[Task, MailAddress] =
    circeEntityDecoder[Task, MailAddress]
  implicit lazy val emailEncoder: Encoder[MailAddress] = deriveEncoder[MailAddress]
  implicit lazy val emailEntityEncoder: EntityEncoder[Task, MailAddress] =
    circeEntityEncoder[Task, MailAddress]

  implicit lazy val methodDecoder: Decoder[Method] =
    Decoder[String].emap(Method.fromString(_).leftMap(_.sanitized))
  implicit lazy val methodEntityDecoder: EntityDecoder[Task, Method] =
    circeEntityDecoder[Task, Method]
  implicit lazy val methodEncoder: Encoder[Method] = Encoder[String].contramap(_.name)
  implicit lazy val methodEntityEncoder: EntityEncoder[Task, Method] =
    circeEntityEncoder[Task, Method]

  final case class Link(href: String, rel: String, method: Method)

  object Link {
    implicit lazy val decoder: Decoder[Link] = deriveDecoder[Link]
    implicit lazy val encoder: Encoder[Link] = deriveEncoder[Link]
    implicit lazy val entityDecoder: EntityDecoder[Task, Link] =
      circeEntityDecoder[Task, Link]
    implicit lazy val entityEncoder: EntityEncoder[Task, Link] =
      circeEntityEncoder[Task, Link]
  }

  final case class CreateOptionRequest(title: String, description: Option[String])

  object CreateOptionRequest {
    implicit lazy val decoder: Decoder[CreateOptionRequest] = deriveDecoder[CreateOptionRequest]
    implicit lazy val encoder: Encoder[CreateOptionRequest] = deriveEncoder[CreateOptionRequest]
    implicit lazy val entityDecoder: EntityDecoder[Task, CreateOptionRequest] =
      circeEntityDecoder[Task, CreateOptionRequest]
    implicit lazy val entityEncoder: EntityEncoder[Task, CreateOptionRequest] =
      circeEntityEncoder[Task, CreateOptionRequest]
  }

  final case class CreateElectionRequest(
      title: String,
      description: Option[String],
      admin: MailAddress,
      options: List[CreateOptionRequest],
      voters: List[MailAddress],
  )

  object CreateElectionRequest {
    implicit lazy val decoder: Decoder[CreateElectionRequest] = deriveDecoder[CreateElectionRequest]
    implicit lazy val encoder: Encoder[CreateElectionRequest] = deriveEncoder[CreateElectionRequest]
    implicit lazy val entityDecoder: EntityDecoder[Task, CreateElectionRequest] =
      circeEntityDecoder[Task, CreateElectionRequest]
    implicit lazy val entityEncoder: EntityEncoder[Task, CreateElectionRequest] =
      circeEntityEncoder[Task, CreateElectionRequest]
  }

  final case class LinksResponse(links: List[Link])

  object LinksResponse {
    implicit lazy val encoder: Encoder[LinksResponse] = deriveEncoder[LinksResponse]
    implicit lazy val entityEncoder: EntityEncoder[Task, LinksResponse] =
      circeEntityEncoder[Task, LinksResponse]
    implicit lazy val decoder: Decoder[LinksResponse] = deriveDecoder[LinksResponse]
    implicit lazy val entityDecoder: EntityDecoder[Task, LinksResponse] =
      circeEntityDecoder[Task, LinksResponse]
  }

  final case class GetOptionResponse(id: Int, title: String, description: Option[String])

  object GetOptionResponse {
    implicit lazy val encoder: Encoder[GetOptionResponse] = deriveEncoder[GetOptionResponse]
    implicit lazy val entityEncoder: EntityEncoder[Task, GetOptionResponse] =
      circeEntityEncoder[Task, GetOptionResponse]
    implicit lazy val decoder: Decoder[GetOptionResponse] = deriveDecoder[GetOptionResponse]
    implicit lazy val entityDecoder: EntityDecoder[Task, GetOptionResponse] =
      circeEntityDecoder[Task, GetOptionResponse]
  }

  final case class GetElectionResponse(
      title: String,
      titleMangled: String,
      description: Option[String],
      admin: MailAddress,
      options: List[GetOptionResponse],
      voter: MailAddress,
      voterToken: String,
      links: List[Link],
  )

  object GetElectionResponse {
    implicit lazy val encoder: Encoder[GetElectionResponse] = deriveEncoder[GetElectionResponse]
    implicit lazy val entityEncoder: EntityEncoder[Task, GetElectionResponse] =
      circeEntityEncoder[Task, GetElectionResponse]
    implicit lazy val decoder: Decoder[GetElectionResponse] = deriveDecoder[GetElectionResponse]
    implicit lazy val entityDecoder: EntityDecoder[Task, GetElectionResponse] =
      circeEntityDecoder[Task, GetElectionResponse]

  }

  final case class GetVoterResponse(voter: MailAddress, voted: Boolean)

  object GetVoterResponse {
    implicit lazy val encoder: Encoder[GetVoterResponse] = deriveEncoder[GetVoterResponse]
    implicit lazy val entityEncoder: EntityEncoder[Task, GetVoterResponse] =
      circeEntityEncoder[Task, GetVoterResponse]
    implicit lazy val decoder: Decoder[GetVoterResponse] = deriveDecoder[GetVoterResponse]
    implicit lazy val entityDecoder: EntityDecoder[Task, GetVoterResponse] =
      circeEntityDecoder[Task, GetVoterResponse]
  }

  final case class GetElectionAdminResponse(
      title: String,
      titleMangled: String,
      description: Option[String],
      admin: MailAddress,
      adminToken: String,
      options: List[GetOptionResponse],
      voters: List[GetVoterResponse],
      links: List[Link],
  )

  object GetElectionAdminResponse {
    implicit lazy val encoder: Encoder[GetElectionAdminResponse] = deriveEncoder[GetElectionAdminResponse]
    implicit lazy val entityEncoder: EntityEncoder[Task, GetElectionAdminResponse] =
      circeEntityEncoder[Task, GetElectionAdminResponse]
    implicit lazy val decoder: Decoder[GetElectionAdminResponse] = deriveDecoder[GetElectionAdminResponse]
    implicit lazy val entityDecoder: EntityDecoder[Task, GetElectionAdminResponse] =
      circeEntityDecoder[Task, GetElectionAdminResponse]

  }

  final case class CastVoteRequest(preferences: List[Int])

  object CastVoteRequest {
    implicit lazy val encoder: Encoder[CastVoteRequest] = deriveEncoder[CastVoteRequest]
    implicit lazy val entityEncoder: EntityEncoder[Task, CastVoteRequest] =
      circeEntityEncoder[Task, CastVoteRequest]
    implicit lazy val decoder: Decoder[CastVoteRequest] = deriveDecoder[CastVoteRequest]
    implicit lazy val entityDecoder: EntityDecoder[Task, CastVoteRequest] =
      circeEntityDecoder[Task, CastVoteRequest]
  }

  def make(voting: Voting, clock: Clock.Service): Http = new Http(voting, clock)

  val layer: URLayer[Has[Voting] with Has[Clock.Service], Has[Http]] =
    (make _).toLayer

}
