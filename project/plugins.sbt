resolvers += Resolver.sonatypeRepo("public")

val scalaJSVersion =
  Option(System.getenv("SCALAJS_VERSION")).getOrElse("1.0.1")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % scalaJSVersion)
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.3.2")
addSbtPlugin("io.github.cquiroz" % "sbt-locales" % "0.3.0")
addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.5.2")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.11")

// Dependencies for custom tasks
libraryDependencies := {
  CrossVersion.partialVersion(scalaVersion.value) match {
    // if scala 2.11+ is used, add dependency on scala-xml module
    case Some((2, scalaMajor)) if scalaMajor >= 11 =>
      libraryDependencies.value ++ Seq("org.scala-lang.modules" %% "scala-xml" % "1.2.0")
    case _ =>
      libraryDependencies.value
  }
}
