package cz.idealiste.idealvoting.server

import org.http4s.server._
import zio._
import zio.blocking.Blocking
import zio.random.Random

object Main extends App {

  override def run(args: List[String]): URIO[ZEnv, ExitCode] =
    server.useForever.exitCode

  val server: RManaged[Blocking with Random, Server[RIO[Random, *]]] = {

    for {
      db <- Db.make(
        Config.Db(
          "jdbc:mysql://localhost:3306/idealvoting",
          "idealvoting",
          "idealvoting",
        ),
      )
      voting = new Voting(Config.Voting(), db)
      http <- Http.make(Config.Http("localhost", 8080), voting)
    } yield http
  }

}
