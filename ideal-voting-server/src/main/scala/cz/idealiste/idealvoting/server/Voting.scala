package cz.idealiste.idealvoting.server

import cats.implicits._
import cz.idealiste.idealvoting.server.Http._
import zio.interop.catz.core._
import zio.random.Random
import zio.{RIO, random}

class Voting(config: Config.Voting, db: Db) {

  def createElections(request: CreateElectionsRequest): RIO[Random, String] = {
    for {
      adminToken <- random.nextString(config.tokenLength)
      votersAndTokens <- request.voters.traverse { voter =>
        random.nextString(config.tokenLength).map((voter, _))
      }
      _ <- db.createElections(
        request.name,
        (request.admin, adminToken),
        request.options,
        votersAndTokens,
      )
    } yield adminToken
  }

  def viewElections(token: String): ElectionsView = {
    ElectionsView.Voter("", token, List())
  }
}
