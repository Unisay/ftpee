name := "ftpee"

version := "1.0"

scalaVersion := "2.12.0"

val slf4jVersion = "1.7.21"
val catsVersion = "0.8.1"
val commonsNetVersion = "3.5"
val scalatestVersion = "3.0.0"
val mockFtpServerVersion = "2.7"

libraryDependencies += "org.slf4j" % "slf4j-api" % slf4jVersion
libraryDependencies += "org.typelevel" %% "cats-macros" % catsVersion
libraryDependencies += "org.typelevel" %% "cats-kernel" % catsVersion
libraryDependencies += "org.typelevel" %% "cats-core" % catsVersion
libraryDependencies += "org.typelevel" %% "cats-free" % catsVersion
libraryDependencies += "commons-net" % "commons-net" % commonsNetVersion

libraryDependencies += "org.slf4j" % "slf4j-simple" % slf4jVersion % "test"
libraryDependencies += "org.scalatest" %% "scalatest" % scalatestVersion % "test"
libraryDependencies += "org.mockftpserver" % "MockFtpServer" % mockFtpServerVersion % "test"

scalacOptions ++= Seq(
  "-unchecked",
  "-feature",
  "-deprecation:false",
  "-Xlint",
  "-Xcheckinit",
  "-Ywarn-unused-import",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Ywarn-dead-code",
  "-Yno-adapted-args",
  "-language:_",
  "-target:jvm-1.8",
  "-encoding", "UTF-8"
)

resolvers += Resolver.sonatypeRepo("releases")

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3")
