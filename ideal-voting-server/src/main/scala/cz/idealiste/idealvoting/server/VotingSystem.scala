package cz.idealiste.idealvoting.server

trait VotingSystem {
  // TODO: work with domain model entities, not just a damned list of ints!
  def computePositions(options: List[Int], votes: List[List[Int]]): List[Int]
}
