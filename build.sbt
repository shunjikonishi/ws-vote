name := "ws-button"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  cache,
  "net.debasishg" % "redisclient_2.10" % "2.11",
  "org.webjars" %% "webjars-play" % "2.2.1",
  "org.webjars" % "jquery" % "1.10.2-1",
  "org.webjars" % "bootstrap" % "3.0.3",
  "org.java-websocket" % "Java-WebSocket" % "1.3.0",
  "com.newrelic.agent.java" % "newrelic-agent" % "3.4.2"
)     

play.Project.playScalaSettings
