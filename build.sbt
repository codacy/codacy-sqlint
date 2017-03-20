import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}

name := """codacy-engine-sqlint"""

version := "1.0-SNAPSHOT"

val languageVersion = "2.11.7"

scalaVersion := languageVersion

resolvers ++= Seq(
  "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/releases"
)

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % "2.3.10",
  "com.codacy" %% "codacy-engine-scala-seed" % "2.7.2"
)

enablePlugins(JavaAppPackaging)

enablePlugins(DockerPlugin)

version in Docker := "1.0"

val sqlintVersion = "0.1.4"

mappings in Universal <++= (resourceDirectory in Compile) map { (resourceDir: File) =>
  val src = resourceDir / "docs"
  val dest = "/docs"

  for {
    path <- (src ***).get
    if !path.isDirectory
  } yield path -> path.toString.replaceFirst(src.toString, dest)
}

val installAll =
  s"""apk --no-cache add bash build-base ruby ruby-dev tar curl &&
     |apk add --update ca-certificates &&
     |gem install --no-ri --no-rdoc sqlint -v $sqlintVersion &&
     |gem cleanup &&
     |apk del build-base ruby-dev tar curl &&
     |rm -rf /tmp/* &&
     |rm -rf /var/cache/apk/*""".stripMargin.replaceAll(System.lineSeparator(), " ")

val dockerUser = "docker"
val dockerGroup = "docker"

daemonUser in Docker := dockerUser

daemonGroup in Docker := dockerGroup

dockerBaseImage := "develar/java"

dockerCommands := dockerCommands.value.flatMap {
  case cmd@Cmd("WORKDIR", _) => List(cmd,
    Cmd("RUN", installAll)
  )
  case cmd@(Cmd("ADD", "opt /opt")) => List(cmd,
    Cmd("RUN", "mv /opt/docker/docs /docs"),
    Cmd("RUN", "adduser --uid 2004 --disabled-password --gecos \"\" docker"),
    ExecCmd("RUN", Seq("chown", "-R", s"$dockerUser:$dockerGroup", "/docs"): _*)
  )
  case other => List(other)
}
