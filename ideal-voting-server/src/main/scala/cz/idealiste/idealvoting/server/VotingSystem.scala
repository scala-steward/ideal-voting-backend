package cz.idealiste.idealvoting.server

import zio._

import scala.annotation.nowarn

class VotingSystem {
  @nowarn("msg=never used")
  def computePositions(options: List[Int], votes: List[List[Int]]): List[Int] = options.reverse
}

object VotingSystem {

  def make(): VotingSystem =
    new VotingSystem()

  val layer: ULayer[Has[VotingSystem]] =
    (make _).toLayer
}
