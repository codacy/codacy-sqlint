import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}
import sjsonnew._
import sjsonnew.BasicJsonProtocol._
import sjsonnew.support.scalajson.unsafe._

name := "codacy-sqlint"

scalaVersion := "2.13.1"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % "2.7.4",
  "com.codacy" %% "codacy-engine-scala-seed" % "5.0.1"
)

Universal / javaOptions ++= Seq("-XX:MinRAMPercentage=60.0", "-XX:MaxRAMPercentage=90.0")

enablePlugins(JavaAppPackaging)

enablePlugins(DockerPlugin)

version in Docker := "1.0"

lazy val toolVersion = settingKey[String]("The version of the underlying tool retrieved from patterns.json")

toolVersion := {
  case class Patterns(name: String, version: String)
  implicit val patternsIso: IsoLList[Patterns] =
    LList.isoCurried((p: Patterns) => ("name", p.name) :*: ("version", p.version) :*: LNil) {
      case (_, n) :*: (_, v) :*: LNil => Patterns(n, v)
    }

  val jsonFile = (resourceDirectory in Compile).value / "docs" / "patterns.json"
  val json = Parser.parseFromFile(jsonFile)
  val patterns = json.flatMap(Converter.fromJson[Patterns])
  patterns.get.version
}

mappings in Universal ++= {
  (Compile / resourceDirectory).map { resourceDir: File =>
    val src = resourceDir / "docs"
    val dest = "/docs"

    for {
      path <- src.allPaths.get if !path.isDirectory
    } yield path -> path.toString.replaceFirst(src.toString, dest)
  }
}.value

def installAll(toolVersion: String) =
  s"""apk --no-cache add bash build-base ruby ruby-dev ruby-json tar curl &&
     |apk --no-cache add --update ca-certificates &&
     |gem install --no-ri --no-rdoc sqlint -v $toolVersion &&
     |gem cleanup &&
     |apk del build-base ruby-dev tar curl &&
     |rm -rf /tmp/*""".stripMargin.replaceAll(System.lineSeparator(), " ")

val dockerUser = "docker"
val dockerGroup = "docker"

daemonUser in Docker := dockerUser

daemonGroup in Docker := dockerGroup

dockerBaseImage := "openjdk:8-jre-alpine"

dockerCommands := {
  dockerCommands.value.flatMap {
    case cmd @ Cmd("ADD", _) =>
      List(
        Cmd("RUN", s"adduser -u 2004 -D $dockerUser"),
        cmd,
        Cmd("RUN", installAll(toolVersion.value)),
        Cmd("RUN", "mv /opt/docker/docs /docs"),
        ExecCmd("RUN", "chown", "-R", s"$dockerUser:$dockerGroup", "/docs")
      )
    case other => List(other)
  }
}
