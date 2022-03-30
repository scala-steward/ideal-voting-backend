package cz.idealiste.idealvoting.server

import org.http4s.HttpApp
import zio.Task

trait Http {
  def httpApp: HttpApp[Task]
}
