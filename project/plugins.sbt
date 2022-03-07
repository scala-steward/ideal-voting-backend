addSbtPlugin("ch.epfl.scala" % "sbt-version-policy" % "2.0.1")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.10")
addSbtPlugin("com.github.sideeffffect" % "sbt-decent-scala" % "0.7.0+91-be6f7568")
addSbtPlugin("dev.guardrail" % "sbt-guardrail" % "0.68.1")
libraryDependencies ++= List(
  "org.snakeyaml" % "snakeyaml-engine" % "2.3",
)
