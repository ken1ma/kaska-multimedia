// https://www.scala-sbt.org/1.x/docs/Multi-Project.html
lazy val commonSettings = Seq(
  version := "0.1.0",

  scalaVersion := "3.1.3",
  scalacOptions ++= Seq(
    // the default settings from https://scastie.scala-lang.org
    "-encoding", "UTF-8",
    "-deprecation",
    "-feature",
    "-unchecked",
  ),

  assembly / assemblyMergeStrategy := {
    case PathList("META-INF", "versions", _, "module-info.class") => MergeStrategy.discard
    case PathList("META-INF", "native-image", _, "jnijavacpp", "jni-config.json") => MergeStrategy.first
    case PathList("META-INF", "native-image", _, "jnijavacpp", "reflect-config.json") => MergeStrategy.first
    case x => (ThisBuild / assemblyMergeStrategy).value(x)
  },
  // prepend the project name in the assembly jar name
  assembly / assemblyJarName := s"${(assembly / assemblyJarName).value}",
)

lazy val buildInfoSettings = Seq(
  buildInfoKeys := Seq[BuildInfoKey](version),
  buildInfoOptions ++= Seq(
    BuildInfoOption.BuildTime,
    BuildInfoOption.Traits("jp.ken1ma.kaska.BuildInfoHelper"),
  )
)

lazy val root = (project in file("."))
  .aggregate(tool, core)

lazy val tool = (project in file("tool"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    commonSettings,
    buildInfoSettings,
    libraryDependencies ++= Seq(
      "com.monovore" %% "decline" % "2.3.0", // command line parser
      "org.scala-lang" %% "scala3-compiler" % scalaVersion.value,
      "org.bytedeco" % "skia-platform" % "2.80.3-1.5.7", // https://mvnrepository.com/artifact/org.bytedeco/skia
      "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.18.0" % Runtime, // log implementation
    ),
    buildInfoPackage := "jp.ken1ma.kaska.multimedia.tool",

    fork := true,
    Compile / run / baseDirectory := baseDirectory.value / "..",
  ).dependsOn(core /*% "compile->compile; runtime->runtime"*/)

lazy val core = (project in file("core"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    commonSettings,
    buildInfoSettings,
    libraryDependencies ++= Seq(
      // https://github.com/bytedeco/javacpp-presets
      // https://github.com/bytedeco/sbt-javacpp could be used to select the binaries for the platform
      "org.bytedeco" % "ffmpeg-platform" % "5.0-1.5.7", // https://mvnrepository.com/artifact/org.bytedeco/ffmpeg

      //"org.typelevel" %% "cats-effect" % "3.3.12", // IO monad
      "com.github.rssh" %% "cps-async-connect-cats-effect" % "0.9.10", // https://github.com/rssh/cps-async-connect since it seems well-maintained than https://typelevel.org/cats-effect/docs/std/async-await
      "co.fs2" %% "fs2-core" % "3.2.12",

      "org.log4s" %% "log4s" % "1.10.0", // log api
      "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.17.2" % Runtime, // log impl
    ),
    buildInfoPackage := "jp.ken1ma.kaska.multimedia",
  )

// customize SBT
Global / onChangedBuildSource := ReloadOnSourceChanges // reload build.sbt automatically
ThisBuild / turbo := true // enable experimental ClassLoaderLayeringStrategy
