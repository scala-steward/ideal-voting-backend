import com.typesafe.tools.mima.core.{DirectMissingMethodProblem, ProblemFilters}

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val OpenApiHelpers = new {

  private def dropExtension(file: File): String = {
    file.getPath.split('.').toList match {
      case l @ List()  => l
      case l @ List(_) => l
      case l           => l.dropRight(1)
    }
  }.mkString(".")

  private def isOpenApiSpec(file: File): Boolean = {
    import org.snakeyaml.engine.v2.api.{Load, LoadSettings}

    import java.util.{Map => JMap}
    import scala.io.Source
    import scala.jdk.CollectionConverters._
    import scala.util.{Try, Using}

    Using(Source.fromFile(file)) { source =>
      val reader = source.bufferedReader()
      val docs = new Load(LoadSettings.builder().build()).loadAllFromReader(reader).asScala
      val yamls = docs.flatMap(d => Try(d.asInstanceOf[JMap[String, Any]].asScala).toOption)
      yamls.exists(_.contains("openapi"))
    }.toOption.getOrElse(false)
  }

  def discoverFilesRelative(base: File, predicate: File => Boolean): List[File] = {
    def recursiveListFiles(f: File): Array[File] = {
      val these = f.listFiles
      these.filter(f => f.isFile && predicate(f)) ++ these.filter(_.isDirectory).flatMap(recursiveListFiles)
    }
    recursiveListFiles(base).flatMap(_.relativeTo(base)).toList
  }

  def createGuardrailTasks(
      sourceDirectory: File,
  )(relativeFileToTasks: (String, String) => List[dev.guardrail.sbt.Types.Args]): List[dev.guardrail.sbt.Types.Args] = {
    val specs = discoverFilesRelative(sourceDirectory, isOpenApiSpec)
    specs.flatMap { relativeFile =>
      val relativeFileString = relativeFile.getPath
      val pkg = dropExtension(relativeFile).replace('/', '.')
      relativeFileToTasks(relativeFileString, pkg)
    }
  }

  def generateOpenApiDoc(outDir: File, openapiSourceDir: File): Int = {
    import java.nio.file.Files
    import scala.sys.process._
    import scala.util.Using
    val openapiFiles = discoverFilesRelative(openapiSourceDir, isOpenApiSpec)
    Files.createDirectories(outDir.toPath)
    def command(openapiFile: File) = {
      val openapiFilePath = openapiFile.getPath
      val withoutExtension = dropExtension(openapiFile)
      val outDirIndividual = outDir / withoutExtension
      Files.createDirectories(outDirIndividual.toPath)
      s"docker run --rm -v $outDirIndividual:/out -v $openapiSourceDir:/openapis openapitools/openapi-generator-cli:v5.2.1 generate -i /openapis/$openapiFilePath -g html2 -o /out"
    }
    val result = openapiFiles.map(command(_) !).sum
    val items = openapiFiles.map { file =>
      val relativePath = dropExtension(file)
      val name = dropExtension(file).replace('/', '.')
      s"""<li><a href="$relativePath/index.html" target="_blank"><code>$name</code></a></li>"""
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
    Compile / guardrailTasks := {
      val base = (Compile / sourceDirectory).value / "openapi"
      OpenApiHelpers.createGuardrailTasks(base) { (relativeFileString, pkg) =>
        List(
          ScalaClient(base / relativeFileString, pkg = pkg, framework = "http4s"),
          ScalaServer(base / relativeFileString, pkg = pkg, framework = "http4s"),
        )
      }
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
      val openapiFiles =
        OpenApiHelpers.discoverFilesRelative(openapiBase, _ => true).map(f => (file(s"$openapiBase/$f"), s"openapi/$f"))
      openapiFiles
    },
    ThisBuild / versionPolicyIntention := Compatibility.BinaryCompatible,
    ThisBuild / versionPolicyIgnoredInternalDependencyVersions := Some("^\\d+\\.\\d+\\.\\d+\\+\\d+".r),
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
      Dependencies.jackson,
      Dependencies.liquibaseSlf4j % "runtime",
      Dependencies.logback,
      Dependencies.logbackJackson,
      Dependencies.logbackJson,
      Dependencies.mariadb,
      Dependencies.pprint,
      Dependencies.snakeyaml,
      Dependencies.zioDoobieLiquibase,
      Dependencies.zioLoggingSlf4j,
      Dependencies.zioMagic,
      // Test
      Dependencies.monocle % Test,
      Dependencies.zioTest % Test,
      Dependencies.zioTestcontainers % Test,
      Dependencies.zioTestSbt % Test,
    ),
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
  ThisBuild / scalafixDependencies ++= List(
    Dependencies.zioMagicComments,
  ),
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  missinglinkExcludedDependencies ++= List(
    moduleFilter(organization = "ch.qos.logback", name = "logback-classic"),
    moduleFilter(organization = "ch.qos.logback", name = "logback-core"),
    moduleFilter(organization = "com.zaxxer", name = "HikariCP"),
    moduleFilter(organization = "org.slf4j", name = "slf4j-api"),
  ),
  missinglinkIgnoreDestinationPackages ++= List(
    IgnoredPackage("java.sql"), // https://github.com/tpolecat/doobie/pull/1632
    IgnoredPackage("org.osgi.framework"),
  ),
  mimaReportBinaryIssues := {},
  // https://github.com/olafurpg/sbt-ci-release/issues/181
  sonatypeCredentialHost := "s01.oss.sonatype.org",
  sonatypeRepository := "https://s01.oss.sonatype.org/service/local",
)

lazy val generateOpenApiDocTask: TaskKey[Unit] = TaskKey[Unit]("generateOpenApiDoc")

addCommandAlias(
  "ci",
  "; check; idealVotingContract/versionPolicyCheck; +idealVotingContract/generateOpenApiDoc; +publishLocal",
)
addCommandAlias(
  "cipublish",
  "; check; idealVotingContract/versionCheck; +idealVotingContract/generateOpenApiDoc; ci-release",
)
