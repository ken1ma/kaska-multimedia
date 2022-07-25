// generate build info
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")

// generate fat jar 
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "1.2.0")

// dependencyUpdates task to list dependencies that have updates
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.6.3")
