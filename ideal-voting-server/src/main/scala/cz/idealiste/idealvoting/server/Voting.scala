package cz.idealiste.idealvoting.server

import cats.data.{NonEmptyList, NonEmptySet, ValidatedNec}
import cats.implicits._
import cz.idealiste.idealvoting.server.Voting._
import emil.MailAddress
import zio.Task

import java.time.OffsetDateTime
import scala.collection.immutable.SortedSet

trait Voting {
  def createElection(create: CreateElection, now: OffsetDateTime): Task[(String, String)]
  def viewElection(token: String): Task[Option[ElectionView]]
  def viewElectionAdmin(token: String): Task[Option[ElectionViewAdmin]]
  def castVote(token: String, preferences: List[Int]): Task[CastVoteResult]
  def endElection(token: String, now: OffsetDateTime): Task[EndElectionResult]
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
}
