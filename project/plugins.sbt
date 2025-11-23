addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.11.2")
addSbtPlugin("com.github.sideeffffect" % "sbt-decent-scala" % "1.1.5")
addSbtPlugin("dev.guardrail" % "sbt-guardrail" % "1.0.0-M1")
addSbtPlugin("com.github.sideeffffect" % "sbt-github-actions-logger" % "0.1.1")
libraryDependencies ++= List(
  "org.snakeyaml" % "snakeyaml-engine" % "3.0.1",
  "dev.guardrail" %% "guardrail-scala-http4s" % "1.0.0-M1",
)
