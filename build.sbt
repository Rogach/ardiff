name := """ardiff"""

autoScalaLibrary := false

javacOptions ++= Seq("-Xlint:unchecked")

libraryDependencies ++= Seq(
  "org.apache.commons" % "commons-compress" % "1.12",
  "com.nothome" % "javaxdelta" % "2.0.1",

  "com.novocode" % "junit-interface" % "0.11" % "test",
  "commons-io" % "commons-io" % "2.5" % "test"
)
