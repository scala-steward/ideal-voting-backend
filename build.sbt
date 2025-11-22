import com.typesafe.tools.mima.core.{DirectMissingMethodProblem, ProblemFilters}

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val OpenApiHelpers = new {

  import GuardrailHelpers._

  def generateOpenApiDoc(outDir: File, openapiSourceDir: File): Int = {
    import java.nio.file.Files
    import scala.sys.process._
    import scala.util.Using
    val openapiFiles = discoverOpenApiFiles(openapiSourceDir)
    Files.createDirectories(outDir.toPath)
    def command(openApiFile: DiscoveredFile) = {
      val outDirIndividual = outDir / openApiFile.fileRelativePathWithoutExtension
      Files.createDirectories(outDirIndividual.toPath)
      s"docker run --rm -v $outDirIndividual:/out -v $openapiSourceDir:/openapis openapitools/openapi-generator-cli:v6.0.1 generate -i /openapis/${openApiFile.fileRelative} -g html2 -o /out"
    }
    val result = openapiFiles.map(command(_) !).sum
    val items = openapiFiles.map { file =>
      s"""<li><a href="${file.fileRelativePathWithoutExtension}/index.html" target="_blank"><code>${file.pkg}</code></a></li>"""
    }
    val index =
      s"""|<!DOCTYPE html>
          |<html>
          |<body>
          |<h2>Index of OpenAPI documentation</h2>
          |<ul>
          |  ${items.mkString("\n  ")}
          |</ul>
          |</body>
          |</html>
          |""".stripMargin
    Using(new java.io.PrintWriter(s"$outDir/index.html"))(_.write(index)).get
    result
  }

}

lazy val root = project
  .in(file("."))
  .settings(commonSettings)
  .settings(
    name := "ideal-voting-backend",
    publish / skip := true,
  )
  .aggregate(
    idealVotingContract,
    idealVotingServer,
  )

lazy val idealVotingContract = project
  .in(file("ideal-voting-contract"))
  .settings(commonSettings)
  .settings(
    name := "ideal-voting-contract",
    Compile / unmanagedSourceDirectories += (Compile / sourceDirectory).value / "openapi",
    Compile / unmanagedResourceDirectories += (Compile / resourceDirectory).value / "openapi",
    Test / unmanagedSourceDirectories += (Test / sourceDirectory).value / "openapi",
    Test / unmanagedResourceDirectories += (Test / resourceDirectory).value / "openapi",
    Compile / guardrailTasks := (Compile / guardrailDiscoveredOpenApiFiles).value.flatMap { openApiFile =>
      List(
        ScalaClient(openApiFile.file, pkg = openApiFile.pkg, framework = "http4s"),
        ScalaServer(openApiFile.file, pkg = openApiFile.pkg, framework = "http4s"),
      )
    },
    generateOpenApiDocTask := {
      val baseDirectoryValue = baseDirectory.value
      val openapiSourceDir = (Compile / sourceDirectory).value / "openapi"
      val openapiResult = OpenApiHelpers.generateOpenApiDoc(baseDirectoryValue / "target-openapi", openapiSourceDir)
      if (openapiResult != 0) {
        sys.error("openapi-generator-cli html failed")
      }
    },
    Compile / packageDoc / mappings ++= {
      val baseDirectoryValue = baseDirectory.value
      val openapiBase = baseDirectoryValue / "target-openapi"
      val openapiFiles = GuardrailHelpers
        .discoverFiles(openapiBase)
        .map(f => (openapiBase / f.fileRelativePath, s"openapi/${f.fileRelative}"))
      openapiFiles
    },
    mimaBinaryIssueFilters ++= List(
      ProblemFilters.exclude[DirectMissingMethodProblem]("*.apply"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("*.copy"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("*.this"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("*.<init>$default$*"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("*.apply$default$*"),
    ),
    Compile / scalacOptions -= "-Xfatal-warnings",
    Compile / scalacOptions += "-Wconf:cat=unused:ws",
    libraryDependencies ++= List(
      Dependencies.http4sCirce,
      Dependencies.http4sClient,
      Dependencies.http4sDsl,
      Dependencies.http4sServer,
    ),
  )
  .enablePlugins(BuildInfoPlugin)

lazy val idealVotingServer = project
  .in(file("ideal-voting-server"))
  .settings(commonSettings)
  .settings(
    name := "ideal-voting-server",
    Compile / mainClass := Some("cz.idealiste.idealvoting.server.Main"),
    libraryDependencies ++= List(
      Dependencies.chimney,
      Dependencies.circeGeneric,
      Dependencies.circeParser,
      Dependencies.commonsLang,
      Dependencies.emil,
      Dependencies.http4sServerEmber,
      Dependencies.jackson,
      Dependencies.liquibaseDoobieZio,
      Dependencies.liquibaseDoobieZioConfig,
      Dependencies.liquibaseSlf4j,
      Dependencies.logback,
      Dependencies.logbackJackson,
      Dependencies.logbackJson,
      Dependencies.mariadb,
      Dependencies.monocle,
      Dependencies.pprint,
      Dependencies.snakeyaml,
      Dependencies.zioConfigTypesafe,
      Dependencies.zioLoggingSlf4j,
      // Test
      Dependencies.testcontainers % Test,
      Dependencies.zioTest % Test,
      Dependencies.zioTestcontainers % Test,
      Dependencies.zioTestSbt % Test,
    ),
    ThisBuild / versionPolicyIntention := Compatibility.None,
  )
  .dependsOn(idealVotingContract)
  .enablePlugins(BuildInfoPlugin)

lazy val commonSettings: List[Def.Setting[_]] = DecentScala.decentScalaSettings ++ List(
  organization := "cz.idealiste",
  homepage := Some(url("https://github.com/Idealiste-cz/ideal-voting-backend")),
  licenses := List("AGPLv3" -> url("https://www.gnu.org/licenses/agpl-3.0.en.html")),
  developers := List(
    Developer(
      "sideeffffect",
      "Ondra Pelech",
      "ondra.pelech@gmail.com",
      url("https://github.com/sideeffffect"),
    ),
  ),
  crossScalaVersions := List(DecentScala.decentScalaVersion213),
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  missinglinkExcludedDependencies ++= List(
    moduleFilter(organization = "ch.qos.logback", name = "logback-classic"),
    moduleFilter(organization = "ch.qos.logback", name = "logback-core"),
    moduleFilter(organization = "com.zaxxer", name = "HikariCP"),
    moduleFilter(organization = "dev.zio", name = "zio-interop-cats_2.13"), // depends on zio-managed
    moduleFilter(organization = "org.slf4j", name = "slf4j-api"),
  ),
  missinglinkIgnoreDestinationPackages ++= List(
    IgnoredPackage("jnr.unixsocket"),
    IgnoredPackage("org.osgi.framework"),
  ),
  scalacOptions += "-Wconf:cat=lint-infer-any&msg=kind-polymorphic:s", // https://github.com/scala/bug/issues/13128
)

lazy val generateOpenApiDocTask: TaskKey[Unit] = TaskKey[Unit]("generateOpenApiDoc")

addCommandAlias(
  "ci",
  "; check; +idealVotingContract/generateOpenApiDoc; +publishLocal",
)
addCommandAlias(
  "cipublish",
  "; check; idealVotingContract/versionCheck; +idealVotingContract/generateOpenApiDoc; ci-release",
)
