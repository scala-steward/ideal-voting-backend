package cz.idealiste.idealvoting.server

import org.http4s._
import org.http4s.implicits._
import zio.test.Assertion._
import zio.test._

object MainSpec extends DefaultRunnableSpec {
  def spec: ZSpec[Environment, Failure] =
    suite("Service")(
      testM("/status should return OK") {
        val response = Main.httpApp.run(Request(method = Method.GET, uri = uri"/v1/status"))
        assertM(response.map(_.status))(equalTo(Status.Ok))
      },
    )
}
