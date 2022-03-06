package cz.idealiste.idealvoting.server

import cats.data.{NonEmptyList, NonEmptySet, ValidatedNec}
import cats.implicits._
import cz.idealiste.idealvoting.server
import cz.idealiste.idealvoting.server.Voting._
import emil.MailAddress
import org.apache.commons.lang3.StringUtils
import zio._
import zio.config._
import zio.config.magnolia.DeriveConfigDescriptor
import zio.interop.catz.core._
import zio.logging.Logger
import zio.random.Random

import java.time.OffsetDateTime
import scala.collection.immutable.SortedSet

class Voting(
    config: Config,
    db: Db,
    votingSystem: VotingSystem,
    logger: Logger[String],
    random: Random.Service,
) {

  // TODO: distinct type for Voter token and Admin token and represent internally as UUID
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
    ElectionView(election.metadata, admin, election.options, voter, election.result.map(ResultView(_, election.votes)))
  }

  private def electionToViewAdmin(election: Election): ElectionViewAdmin = {
    val voters = election.voters.map { case Voter(email, _, voted) => VoterView(email, voted) }
    ElectionViewAdmin(
      election.metadata,
      election.admin,
      election.options,
      voters,
      election.result.map(ResultView(_, election.votes)),
    )
  }

  def createElection(create: CreateElection, now: OffsetDateTime): Task[(String, String)] = {
    for {
      adminToken <- generateToken
      voters <- create.voters.traverse { email =>
        generateToken.map(Voter(email, _, voted = false))
      }
      titleMangled = mangleTitle(create.title)
      electionMetadata = ElectionMetadata(create.title, titleMangled, create.description, now)
      admin = Admin(create.admin, adminToken)
      options = create.options.zipWithIndex.map { case (CreateOption(title, description), id) =>
        BallotOption(id, title, description)
      }
      () <- logger.debug(s"Creating election $electionMetadata with admin $admin, voters $voters and options $options.")
      () <- db.createElection(electionMetadata, admin, options, voters)
      () <- logger.info(s"Created election $titleMangled.")
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
      result <- election match {
        case None =>
          logger.info(s"Couldn't cast vote, because election for token $token not found.") >>
            Task.succeed(VoteInsertResult.TokenNotFound)
        case Some(election) =>
          Vote.make(preferences, election.optionsMap) match {
            case Left(invalidVote) =>
              logger.info(
                s"Couldn't cast vote for election ${election.metadata.titleMangled} because of $invalidVote",
              ) >> Task.succeed(invalidVote)
            case Right(vote) =>
              db.castVote(token, vote)
                .flatTap(r => // TODO: logging for each case as in `endElection`
                  logger.info(s"Casting vote for election ${election.metadata.titleMangled} with result $r."),
                )
          }
      }
    } yield result
  }

  def endElection(token: String, now: OffsetDateTime): Task[EndElectionResult] = for {
    election <- db.readElectionAdmin(token)
    result <- election match {
      case None =>
        logger.info(s"Couldn't end election, because election for token $token not found.") >>
          Task.succeed(EndElectionResult.TokenNotFound)
      case Some(Election(_, _, _, _, _, _, Some(Result(ended, _)))) =>
        logger.info(s"Couldn't end election, because election for token $token already ended on $ended.") >>
          Task.succeed(EndElectionResult.ElectionAlreadyEnded)
      case Some(election) =>
        val positions = votingSystem.computePositions(election.options.map(_.id), election.votes.map(_.preferences))
        db.endElection(token, positions, now).flatTap {
          case EndElectionResult.TokenNotFound =>
            logger.info(s"Couldn't end election, because election for token $token not found (from DB).")
          case EndElectionResult.ElectionAlreadyEnded =>
            logger.info(s"Couldn't end election, because election for token $token already ended (from DB).")
          case EndElectionResult.SuccessfullyEnded =>
            logger.info(s"Successfully ended election for token $token with positions $positions.")
        }
    }
  } yield result
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
  )

  final case class Admin(email: MailAddress, token: String)

  final case class BallotOption(id: Int, title: String, description: Option[String])

  final case class Voter(email: MailAddress, token: String, voted: Boolean)

  // TODO: use NonEmptyList where applicable
  final case class Election(
      metadata: ElectionMetadata,
      admin: Admin,
      options: List[BallotOption],
      voters: List[Voter],
      votes: List[Vote], // TODO: represent as Map[Vote, Int]
      optionsMap: Map[Int, BallotOption],
      result: Option[Result],
  )

  final case class AdminView(email: MailAddress)

  final case class VoterView(email: MailAddress, voted: Boolean)

  final case class ElectionView(
      metadata: ElectionMetadata,
      admin: AdminView,
      options: List[BallotOption],
      voter: Voter,
      result: Option[ResultView],
  )

  final case class ElectionViewAdmin(
      metadata: ElectionMetadata,
      admin: Admin,
      options: List[BallotOption],
      voters: List[VoterView],
      result: Option[ResultView],
  )

  final case class Result(ended: OffsetDateTime, positions: List[Int])

  final case class ResultView(result: Result, votes: List[Vote])

  sealed abstract case class Vote(preferences: List[Int])
  object Vote {
    def make(preferences: List[Int], optionsMap: Map[Int, BallotOption]): Either[InvalidVote, Vote] = {
      val duplicities = preferences.groupBy(identity).filter(_._2.length > 1).keys.toList.toNel
      val unavailable = SortedSet.from(preferences).diff(optionsMap.keySet).toNes
      (duplicities, unavailable) match {
        case (Some(duplicities), _) => InvalidVote.DuplicateOptions(duplicities).asLeft
        case (_, Some(unavailable)) => InvalidVote.UnavailableOptions(unavailable).asLeft
        case _                      => new Vote(preferences) {}.asRight
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
    final case object ElectionEnded extends VoteInsertResult
    final case object SuccessfullyVoted extends VoteInsertResult
  }

  sealed trait EndElectionResult extends Product with Serializable
  object EndElectionResult {
    final case object TokenNotFound extends EndElectionResult
    final case object ElectionAlreadyEnded extends EndElectionResult
    final case object SuccessfullyEnded extends EndElectionResult
  }

  def make(
      config: Config,
      db: Db,
      votingSystem: VotingSystem,
      logger: Logger[String],
      random: Random.Service,
  ): Voting =
    new Voting(config, db, votingSystem, logger, random)

  val layer: URLayer[
    Has[Config] with Has[Db] with Has[VotingSystem] with Has[Logger[String]] with Has[Random.Service],
    Has[Voting],
  ] =
    (make _).toLayer

  final case class Config(tokenLength: Int = 10)
  object Config {
    val layer: RLayer[Has[server.Config], Has[Config]] = ZIO.service[server.Config].map(_.voting).toLayer
    implicit lazy val configDescriptor: ConfigDescriptor[Config] = DeriveConfigDescriptor.descriptor[Config]
  }
}
