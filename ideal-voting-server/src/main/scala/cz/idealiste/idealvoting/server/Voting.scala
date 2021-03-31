package cz.idealiste.idealvoting.server

import cats.implicits._
import cz.idealiste.idealvoting.server.Voting._
import emil.MailAddress
import org.apache.commons.lang3.StringUtils
import zio._
import zio.interop.catz.core._
import zio.random.Random

class Voting(config: Config.Voting, db: Db, random: Random.Service) {

  private val generateToken: UIO[String] = random
    .nextIntBetween(97, 123)
    .replicateM(config.tokenLength)
    .map(_.map(_.toChar).mkString)

  private def mangleTitle(title: String): String = {
    StringUtils.stripAccents(title).replaceAll("[\\P{Alnum}]+", "-")
  }

  private def electionToView(election: Election, token: String): ElectionView = {
    val admin = AdminView(election.admin.name, election.admin.email)
    val voter = election.voters.find(_.token === token).get
    ElectionView(election.metadata, admin, election.options, voter)
  }

  private def electionToViewAdmin(election: Election): ElectionViewAdmin = {
    val voters = election.voters.map { case Voter(name, email, _, voted) => VoterView(name, email, voted) }
    ElectionViewAdmin(election.metadata, election.admin, election.options, voters)
  }

  def createElection(create: CreateElection): Task[ElectionViewAdmin] = {
    for {
      adminToken <- generateToken
      voters <- create.voters.traverse { email =>
        generateToken.map(Voter(None, email, _, voted = false))
      }
      titleMangled = mangleTitle(create.title)
      electionMetadata = ElectionMetadata(create.title, titleMangled, create.description)
      admin = Admin(None, create.admin, adminToken)
      options = create.options.zipWithIndex.map { case (CreateOption(title, description), id) =>
        BallotOption(id, title, description)
      }
      election = Election(electionMetadata, admin, options, voters)
      _ <- db.createElection(election)
      view = electionToViewAdmin(election)
    } yield view
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

  final case class ElectionMetadata(title: String, titleMangled: String, description: Option[String])

  final case class Admin(name: Option[String], email: MailAddress, token: String)

  final case class BallotOption(id: Int, title: String, description: Option[String])

  final case class Voter(name: Option[String], email: MailAddress, token: String, voted: Boolean)

  final case class Election(
      metadata: ElectionMetadata,
      admin: Admin,
      options: List[BallotOption],
      voters: List[Voter],
  )

  final case class AdminView(name: Option[String], email: MailAddress)

  final case class VoterView(name: Option[String], email: MailAddress, voted: Boolean)

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

  def make(config: Config.Voting, db: Db, random: Random.Service): Voting = new Voting(config, db, random)

  val layer: URLayer[Has[Config.Voting] with Has[Db] with Has[Random.Service], Has[Voting]] =
    ZLayer.fromServices[Config.Voting, Db, Random.Service, Voting](make)
}
