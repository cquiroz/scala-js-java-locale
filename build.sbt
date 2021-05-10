import sbtcrossproject.CrossPlugin.autoImport.{ CrossType, crossProject }
import sbt.Keys._
import locales._

lazy val cldrApiVersion = "2.6.0"

Global / onChangedBuildSource := ReloadOnSourceChanges

resolvers in Global += Resolver.sonatypeRepo("public")

ThisBuild / scalaVersion := "2.13.3"
ThisBuild / crossScalaVersions := Seq("2.11.12", "2.12.13", "2.13.3", "3.0.0-RC2", "3.0.0-RC3")

ThisBuild / githubWorkflowTargetTags ++= Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches +=
  RefPredicate.StartsWith(Ref.Tag("v"))

ThisBuild / githubWorkflowPublish := Seq(WorkflowStep.Sbt(List("ci-release")))

ThisBuild / githubWorkflowPublishPreamble +=
  WorkflowStep.Use("olafurpg", "setup-gpg", "v3")

ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Sbt(
    List("ci-release"),
    env = Map(
      "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
      "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
    )
  )
)

val commonSettings: Seq[Setting[_]] = Seq(
  organization := "io.github.cquiroz",
  scalacOptions ~= (_.filterNot(
    Set(
      "-Wdead-code",
      "-Ywarn-dead-code",
      "-Wunused:params",
      "-Ywarn-unused:params",
      "-Wvalue-discard",
      "-Ywarn-value-discard"
    )
  )),
  Compile / doc / scalacOptions := Seq(),
  Compile / unmanagedSourceDirectories ++= scalaVersionSpecificFolders("main",
                                                                       baseDirectory.value,
                                                                       scalaVersion.value
  ),
  Test / unmanagedSourceDirectories ++= scalaVersionSpecificFolders("test",
                                                                    baseDirectory.value,
                                                                    scalaVersion.value
  )
)

inThisBuild(
  List(
    organization := "io.github.cquiroz",
    homepage := Some(url("https://github.com/cquiroz/scala-java-locales")),
    licenses := Seq("BSD 3-Clause License" -> url("https://opensource.org/licenses/BSD-3-Clause")),
    developers := List(
      Developer("cquiroz",
                "Carlos Quiroz",
                "carlos.m.quiroz@gmail.com",
                url("https://github.com/cquiroz")
      ),
      Developer("er1c", "Eric Peters", "", url("https://github.com/er1c")),
      Developer("alonsodomin", "A. Alonso Dominguez", "", url("https://github.com/alonsodomin")),
      Developer("mkotsbak", "Marius B. Kotsbak", "", url("https://github.com/mkotsbak")),
      Developer("TimothyKlim", "Timothy Klim", "", url("https://github.com/TimothyKlim"))
    ),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/cquiroz/scala-java-locales"),
        "scm:git:git@github.com:cquiroz/scala-java-locales.git"
      )
    )
  )
)

def scalaVersionSpecificFolders(srcName: String, srcBaseDir: java.io.File, scalaVersion: String) = {
  def extraDirs(suffix: String) =
    List(CrossType.Pure, CrossType.Full)
      .flatMap(_.sharedSrcDir(srcBaseDir, srcName).toList.map(f => file(f.getPath + suffix)))
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, y))     => extraDirs("-2.x") ++ (if (y >= 13) extraDirs("-2.13+") else Nil)
    case Some((0 | 3, _)) => extraDirs("-2.13+") ++ extraDirs("-3.x")
    case _                => Nil
  }
}

lazy val scalajs_locales: Project = project
  .in(file("."))
  .settings(commonSettings: _*)
  .settings(
    name := "locales",
    publish := {},
    publishLocal := {},
    publishArtifact := false
  )
  // don't include scala-native by default
  .aggregate(core.js,
             core.jvm,
             core.native,
             testSuite.js,
             testSuite.jvm,
             testSuite.native,
             localesFullDb,
             localesFullCurrenciesDb,
             localesMinimalEnDb,
             demo
  )

lazy val core = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .in(file("core"))
  .settings(commonSettings: _*)
  .settings(
    name := "scala-java-locales",
    libraryDependencies ++= Seq(
      "io.github.cquiroz"      %%% "cldr-api"                % cldrApiVersion
    ),
    scalacOptions ~= (_.filterNot(
      Set(
        "-deprecation",
        "-Xfatal-warnings"
      )
    ))
  )
  .jsSettings(
    scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _)) => Seq("-scalajs-genStaticForwardersForNonTopLevelObjects")
      case _ => Seq("-P:scalajs:genStaticForwardersForNonTopLevelObjects")
    }),
    scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _)) => Seq.empty
      case _ =>
        val tagOrHash =
          if (isSnapshot.value) sys.process.Process("git rev-parse HEAD").lineStream_!.head
          else s"v${version.value}"
        (Compile / sourceDirectories).value.map { dir =>
          val a = dir.toURI.toString
          val g =
            "https://raw.githubusercontent.com/cquiroz/scala-java-locales/" + tagOrHash + "/core/src/main/scala"
          s"-P:scalajs:mapSourceURI:$a->$g/"
        }
    })
  )

lazy val cldrDbVersion = "36.0"

lazy val localesFullCurrenciesDb = project
  .in(file("localesFullCurrenciesDb"))
  .settings(commonSettings: _*)
  .configure(_.enablePlugins(LocalesPlugin))
  .configure(_.enablePlugins(ScalaJSPlugin))
  .settings(
    name := "locales-full-currencies-db",
    cldrVersion := CLDRVersion.Version(cldrDbVersion),
    localesFilter := LocalesFilter.All,
    nsFilter := NumberingSystemFilter.All,
    calendarFilter := CalendarFilter.All,
    currencyFilter := CurrencyFilter.All,
    supportDateTimeFormats := true,
    supportNumberFormats := true,
    supportISOCodes := true,
    libraryDependencies += ("org.portable-scala" %%% "portable-scala-reflect" % "1.1.1").cross(CrossVersion.for3Use2_13)
  )

lazy val localesFullDb = project
  .in(file("localesFullDb"))
  .settings(commonSettings: _*)
  .configure(_.enablePlugins(LocalesPlugin))
  .configure(_.enablePlugins(ScalaJSPlugin))
  .settings(
    name := "locales-full-db",
    cldrVersion := CLDRVersion.Version(cldrDbVersion),
    localesFilter := LocalesFilter.All,
    nsFilter := NumberingSystemFilter.All,
    calendarFilter := CalendarFilter.All,
    currencyFilter := CurrencyFilter.None,
    supportDateTimeFormats := true,
    supportNumberFormats := true,
    supportISOCodes := true,
    libraryDependencies += ("org.portable-scala" %%% "portable-scala-reflect" % "1.1.1").cross(CrossVersion.for3Use2_13)
  )

lazy val localesMinimalEnDb = project
  .in(file("localesMinimalEnDb"))
  .settings(commonSettings: _*)
  .configure(_.enablePlugins(LocalesPlugin))
  .configure(_.enablePlugins(ScalaJSPlugin))
  .settings(
    name := "locales-minimal-en-db",
    cldrVersion := CLDRVersion.Version(cldrDbVersion),
    localesFilter := LocalesFilter.Minimal,
    nsFilter := NumberingSystemFilter.Minimal,
    calendarFilter := CalendarFilter.Minimal,
    currencyFilter := CurrencyFilter.None,
    supportDateTimeFormats := true,
    supportNumberFormats := true,
    supportISOCodes := false,
    libraryDependencies += ("org.portable-scala" %%% "portable-scala-reflect" % "1.1.1").cross(CrossVersion.for3Use2_13)
  )

lazy val testSuite = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .in(file("testSuite"))
  .settings(commonSettings: _*)
  .settings(
    publish := {},
    publishLocal := {},
    publishArtifact := false,
    name := "scala-java-locales test",
    libraryDependencies += "org.scalameta" %%% "munit" % "0.7.25" % Test,
    testFrameworks += new TestFramework("munit.Framework"),
    scalacOptions ~= (_.filterNot(
      Set(
        "-deprecation",
        "-Xfatal-warnings"
      )
    ))
  )
  .jsSettings(Test / parallelExecution := false,
              name := "scala-java-locales testSuite on JS",
              scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule))
  )
  .jsConfigure(_.dependsOn(core.js, macroUtils, localesFullCurrenciesDb))
  .jvmSettings(
    // Fork the JVM test to ensure that the custom flags are set
    Test / fork := true,
    // Use CLDR provider for locales
    // https://docs.oracle.com/javase/8/docs/technotes/guides/intl/enhancements.8.html#cldr
    Test / javaOptions ++= Seq(
      "-Duser.language=en",
      "-Duser.country=",
      "-Djava.locale.providers=CLDR",
      "-Dfile.encoding=UTF8"
    ),
    name := "scala-java-locales testSuite on JVM",
    libraryDependencies += "io.github.cquiroz" %%% "cldr-api" % cldrApiVersion
  )
  .jvmConfigure(_.dependsOn(macroUtils))

lazy val macroUtils = project
  .in(file("macroUtils"))
  .settings(commonSettings)
  .settings(
    name := "macroutils",
    libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _)) => Seq.empty
      case _ => Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
    }),
    scalacOptions ~= (_.filterNot(
      Set(
        "-deprecation",
        "-Xfatal-warnings"
      )
    ))
  )

lazy val demo = project
  .in(file("demo"))
  .configure(_.enablePlugins(ScalaJSPlugin))
  .settings(commonSettings: _*)
  .settings(
    publish := {},
    publishLocal := {},
    publishArtifact := false,
    scalaJSUseMainModuleInitializer := true,
    name := "scala-java-locales demo"
  )
  .dependsOn(core.js)
