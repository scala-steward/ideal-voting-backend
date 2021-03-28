package cz.idealiste.idealvoting.server

import cats.implicits._
import cz.idealiste.idealvoting.server.Http._
import zio.interop.catz.core._
import zio.random.Random
import zio.{RIO, Task, random}

class Voting(config: Config.Voting, db: Db) {

  def createElection(request: CreateElectionRequest): RIO[Random, String] = {
    for {
      adminToken <- random
        .nextIntBetween(97, 123)
        .replicateM(config.tokenLength)
        .map(_.map(_.toChar).mkString)
      votersAndTokens <- request.voters.traverse { voter =>
        random
          .nextIntBetween(97, 123)
          .replicateM(config.tokenLength)
          .map(_.map(_.toChar).mkString)
          .map((voter, _))
      }
      votersNormalizedAndTokens = votersAndTokens.map { case (voterFullname, token) =>
        (voterFullname, voterFullname, token, false)
      }
      _ <- db.createElection(
        request.name,
        (request.admin, adminToken),
        request.options.zipWithIndex,
        votersNormalizedAndTokens,
      )
    } yield adminToken
  }

  def viewElection(token: String): Task[ElectionView] = {
    db.viewElection(token)
  }

  def viewElectionAdmin(token: String): Task[ElectionViewAdmin] = {
    db.viewElectionAdmin(token)
  }

}
