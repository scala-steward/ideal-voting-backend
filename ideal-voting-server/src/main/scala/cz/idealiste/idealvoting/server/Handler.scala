package cz.idealiste.idealvoting.server

import cz.idealiste.idealvoting.contract
import org.http4s.HttpRoutes
import zio.Task

trait Handler extends contract.Handler[Task] {
  def oldRoutes: HttpRoutes[Task]
}
