package cz.idealiste.idealvoting.server

import cats.implicits._
import cz.idealiste.idealvoting.server.Voting._
import emil.MailAddress
import org.apache.commons.lang3.StringUtils
import zio._
import zio.clock.Clock
import zio.interop.catz.core._
import zio.random.Random

import java.time.OffsetDateTime

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

  def viewElection(token: String): Task[ElectionView] = for {
    election <- db.readElection(token)
    electionView = electionToView(election, token)
  } yield electionView

  def viewElectionAdmin(token: String): Task[ElectionViewAdmin] = for {
    election <- db.readElectionAdmin(token)
    electionViewAdmin = electionToViewAdmin(election)
  } yield electionViewAdmin

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

  final case class Vote(preferences: List[BallotOption])

  def make(config: Config.Voting, db: Db, random: Random.Service, clock: Clock.Service): Voting =
    new Voting(config, db, random, clock)

  val layer: URLayer[
    Has[Config.Voting] with Has[Db] with Has[Random.Service] with Has[Clock.Service],
    Has[Voting],
  ] =
    ZLayer.fromServices[Config.Voting, Db, Random.Service, Clock.Service, Voting](make)
}
