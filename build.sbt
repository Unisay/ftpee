name := "ftpee"

version := "1.0"

scalaVersion := "2.12.0"

val catsVersion = "0.8.1"

libraryDependencies += "org.typelevel" %% "cats-macros" % catsVersion
libraryDependencies += "org.typelevel" %% "cats-kernel" % catsVersion
libraryDependencies += "org.typelevel" %% "cats-core" % catsVersion
libraryDependencies += "org.typelevel" %% "cats-free" % catsVersion

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
