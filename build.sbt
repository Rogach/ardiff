name := """ardiff"""

autoScalaLibrary := false

javacOptions ++= Seq("-Xlint:unchecked")

libraryDependencies ++= Seq(
  "org.apache.commons" % "commons-compress" % "1.12",
  "commons-io" % "commons-io" % "2.5",
  "org.tukaani" % "xz" % "1.5",
  "com.nothome" % "javaxdelta" % "2.0.1",

  "com.novocode" % "junit-interface" % "0.11" % "test"
)

assemblyJarName in assembly := "ArchiveDiff.jar"

mainClass in assembly := Some("org.rogach.ardiff.ArchiveDiff")

test in assembly := {}
