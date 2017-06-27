name := """ardiff"""

autoScalaLibrary := false

crossPaths := false

javacOptions ++= Seq("-Xlint:unchecked")

libraryDependencies ++= Seq(
  "org.apache.commons" % "commons-compress" % "1.12",
  "commons-io" % "commons-io" % "2.5",
  "org.tukaani" % "xz" % "1.5",
  "com.nothome" % "javaxdelta" % "2.0.1",

  "com.novocode" % "junit-interface" % "0.11" % "test"
)

assemblyJarName in assembly := "ArchiveDiff-assembly.jar"

mainClass in assembly := Some("org.rogach.ardiff.ArchiveDiff")

test in assembly := {}

lazy val proguard = taskKey[File]("shrink assembled file with proguard")

proguard := {
  val assemblyJar = assembly.value
  val base = baseDirectory.value

  val args =
    IO.read(file("project/proguard.conf"))
    .replace("${java.home}", sys.props("java.home"))

  import _root_.proguard.{Configuration => ProGuardConfiguration, ConfigurationParser, ProGuard}
  val config = new ProGuardConfiguration
  new ConfigurationParser(args, "", base, new java.util.Properties).parse(config)
  new ProGuard(config).execute()

  file("target/ArchiveDiff.jar")
}
