package cz.idealiste.idealvoting.server

final case class Config(db: Config.Db, http: Config.Http, voting: Config.Voting)

object Config {
  final case class Db(
      url: String,
      user: String,
      password: String,
      driverClassName: String = "org.mariadb.jdbc.Driver",
      changeLogFile: String = "db/changelog/db.changelog-master.yaml",
      threadPoolSize: Int = 32,
  )
  final case class Http(host: String, port: Int)
  final case class Voting(tokenLength: Int = 10)
}
