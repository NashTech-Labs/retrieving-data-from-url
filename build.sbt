
name := "GetMetaDataFromUrlServer"

version := "1.0"

scalaVersion := "2.11.7"

val akkaHttpVersion = "2.4.7"

libraryDependencies ++= Seq(
"org.fusesource.jansi"       %  "jansi"                                   % "1.12",
"com.typesafe.akka"          %% "akka-http-experimental"         % akkaHttpVersion,
"com.typesafe.akka"          %% "akka-http-testkit-experimental" % "2.4.2-RC3",
"com.typesafe.akka"          %% "akka-slf4j"                     % akkaHttpVersion,
"com.typesafe.scala-logging" %% "scala-logging"                  % "3.4.0",
  "net.liftweb"                %% "lift-json"                      % "2.6",
  "net.databinder.dispatch" % "dispatch-jsoup_2.10" % "0.11.3"
)

