package cz.idealiste.idealvoting.server

import org.http4s
import zio.Task

trait HttpApp {
  def httpApp: http4s.HttpApp[Task]
}
