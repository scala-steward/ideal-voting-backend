package cz.idealiste.idealvoting.server

import cz.idealiste.idealvoting.server.Db._
import cz.idealiste.idealvoting.server.Voting._
import zio.Task

import java.time.OffsetDateTime

trait Db {
  def createElection(
      metadata: ElectionMetadata,
      admin: Admin,
      options: List[BallotOption],
      voters: List[Voter],
  ): Task[Unit]

  def readElection(token: String): Task[Option[Election]]

  def readElectionAdmin(token: String): Task[Option[Election]]

  def castVote(token: String, vote: Vote): Task[VoteInsertResult]

  def endElection(token: String, positions: List[Int], now: OffsetDateTime): Task[EndElectionResult]
}

object Db {

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
}
