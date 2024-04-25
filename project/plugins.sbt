addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.12")
addSbtPlugin("com.github.sideeffffect" % "sbt-decent-scala" % "1.0.0-6-e3022e4e")
addSbtPlugin("dev.guardrail" % "sbt-guardrail" % "1.0.0-M1")
libraryDependencies ++= List(
  "org.snakeyaml" % "snakeyaml-engine" % "2.4",
  "dev.guardrail" %% "guardrail-scala-http4s" % "1.0.0-M1",
)
