package cz.idealiste.idealvoting.server

import zio._

import scala.annotation.nowarn

final case class VotingSystem() {
  @nowarn("msg=never used")
  def computePositions(options: List[Int], votes: List[List[Int]]): List[Int] = options.reverse
}

object VotingSystem {

  private[server] val layer = (apply _).toLayer
}
