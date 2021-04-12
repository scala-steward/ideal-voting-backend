package cz.idealiste.idealvoting.server

import cats.data.{NonEmptyList, NonEmptySet, ValidatedNec}
import cats.implicits._
import cz.idealiste.idealvoting.server.Voting._
import emil.MailAddress
import org.apache.commons.lang3.StringUtils
import zio._
import zio.clock.Clock
import zio.interop.catz.core._
import zio.random.Random

import java.time.OffsetDateTime
import scala.collection.immutable.SortedSet

class Voting(config: Config.Voting, db: Db, random: Random.Service, clock: Clock.Service) {

  private val generateToken: UIO[String] = random
    .nextIntBetween(97, 123)
    .replicateM(config.tokenLength)
    .map(_.map(_.toChar).mkString)

  private def mangleTitle(title: String): String = {
    StringUtils.stripAccents(title).replaceAll("[\\P{Alnum}]+", "-")
  }

  private def electionToView(election: Election, token: String): ElectionView = {
    val admin = AdminView(election.admin.email)
    val voter = election.voters.find(_.token === token).get
    ElectionView(election.metadata, admin, election.options, voter)
  }

  private def electionToViewAdmin(election: Election): ElectionViewAdmin = {
    val voters = election.voters.map { case Voter(email, _, voted) => VoterView(email, voted) }
    ElectionViewAdmin(election.metadata, election.admin, election.options, voters)
  }

  def createElection(create: CreateElection): Task[(String, String)] = {
    for {
      adminToken <- generateToken
      voters <- create.voters.traverse { email =>
        generateToken.map(Voter(email, _, voted = false))
      }
      now <- clock.currentDateTime
      titleMangled = mangleTitle(create.title)
      electionMetadata = ElectionMetadata(create.title, titleMangled, create.description, now, None)
      admin = Admin(create.admin, adminToken)
      options = create.options.zipWithIndex.map { case (CreateOption(title, description), id) =>
        BallotOption(id, title, description)
      }
      _ <- db.createElection(electionMetadata, admin, options, voters)
    } yield (titleMangled, adminToken)
  }

  def viewElection(token: String): Task[Option[ElectionView]] = for {
    election <- db.readElection(token)
    electionView = election.map(electionToView(_, token))
  } yield electionView

  def viewElectionAdmin(token: String): Task[Option[ElectionViewAdmin]] = for {
    election <- db.readElectionAdmin(token)
    electionViewAdmin = election.map(electionToViewAdmin)
  } yield electionViewAdmin

  def castVote(token: String, preferences: List[Int]): Task[CastVoteResult] = {
    for {
      election <- db.readElection(token)
      result <- election.map(election => Vote.make(preferences, election.optionsMap)) match {
        case None                    => Task.succeed(VoteInsertResult.TokenNotFound)
        case Some(Left(invalidVote)) => Task.succeed(invalidVote)
        case Some(Right(vote))       => db.castVote(token, vote)
      }
    } yield result
  }
}

object Voting {

  final case class CreateOption(title: String, description: Option[String])

  final case class CreateElection(
      title: String,
      description: Option[String],
      admin: MailAddress,
      options: List[CreateOption],
      voters: List[MailAddress],
  )

  final case class ElectionMetadata(
      title: String,
      titleMangled: String,
      description: Option[String],
      started: OffsetDateTime,
      ended: Option[OffsetDateTime],
  )

  final case class Admin(email: MailAddress, token: String)

  final case class BallotOption(id: Int, title: String, description: Option[String])

  final case class Voter(email: MailAddress, token: String, voted: Boolean)

  final case class Election(
      metadata: ElectionMetadata,
      admin: Admin,
      options: List[BallotOption],
      voters: List[Voter],
      votes: List[Vote],
      optionsMap: Map[Int, BallotOption],
  )

  final case class AdminView(email: MailAddress)

  final case class VoterView(email: MailAddress, voted: Boolean)

  final case class ElectionView(
      metadata: ElectionMetadata,
      admin: AdminView,
      options: List[BallotOption],
      voter: Voter,
  )

  final case class ElectionViewAdmin(
      metadata: ElectionMetadata,
      admin: Admin,
      options: List[BallotOption],
      voters: List[VoterView],
  )

  sealed abstract case class Vote(preferences: List[BallotOption])
  object Vote {
    def make(preferences: List[Int], optionsMap: Map[Int, BallotOption]): Either[InvalidVote, Vote] = {
      val duplicities = preferences.groupBy(identity).filter(_._2.length > 1).keys.toList.toNel
      val unavailable = SortedSet.from(preferences).diff(optionsMap.keySet).toNes
      (duplicities, unavailable) match {
        case (Some(duplicities), _) => InvalidVote.DuplicateOptions(duplicities).asLeft
        case (_, Some(unavailable)) => InvalidVote.UnavailableOptions(unavailable).asLeft
        case _                      => new Vote(preferences.map(optionsMap)) {}.asRight
      }
    }
    def makeValidated(
        preferences: List[Int],
        optionsMap: Map[Int, BallotOption],
    ): ValidatedNec[InvalidVote, Vote] = make(preferences, optionsMap).toValidatedNec
  }

  sealed trait CastVoteResult extends Product with Serializable

  sealed abstract class InvalidVote(val message: String) extends Exception(message) with CastVoteResult
  object InvalidVote {
    final case class UnavailableOptions(unavailable: NonEmptySet[Int])
        extends InvalidVote(s"Preferences contain options that are not available: $unavailable")
    final case class DuplicateOptions(duplicities: NonEmptyList[Int])
        extends InvalidVote(s"Preferences contain duplicate options: $duplicities")
  }

  sealed trait VoteInsertResult extends CastVoteResult
  object VoteInsertResult {
    final case object TokenNotFound extends VoteInsertResult
    final case object AlreadyVoted extends VoteInsertResult
    final case object SuccessfullyVoted extends VoteInsertResult
  }

  def make(config: Config.Voting, db: Db, random: Random.Service, clock: Clock.Service): Voting =
    new Voting(config, db, random, clock)

  val layer: URLayer[
    Has[Config.Voting] with Has[Db] with Has[Random.Service] with Has[Clock.Service],
    Has[Voting],
  ] =
    ZLayer.fromServices[Config.Voting, Db, Random.Service, Clock.Service, Voting](make)
}
