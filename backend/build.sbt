name := "chat-server"
version := "1.0.0"
scalaVersion := "2.13.12"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .settings(
    libraryDependencies ++= Seq(
      // Play built-ins
      guice,
      filters,
      ws,
      // Database
      "com.typesafe.play"    %% "play-slick"            % "5.3.1",
      "com.typesafe.play"    %% "play-slick-evolutions" % "5.3.1",
      "com.typesafe.slick"   %% "slick"                 % "3.5.1",
      "com.typesafe.slick"   %% "slick-hikaricp"        % "3.5.1",
      "org.postgresql"        % "postgresql"             % "42.7.3",
      "com.github.tminglei"  %% "slick-pg"              % "0.22.1",
      // Auth
      "com.auth0"             % "java-jwt"               % "4.4.0",
      "de.svenkubiak"         % "jBCrypt"                % "0.4.3",
      // Redis (Lettuce async client)
      "io.lettuce"            % "lettuce-core"           % "6.3.2.RELEASE",
      // JSON
      "com.typesafe.play"    %% "play-json"              % "2.10.4",
      // Test
      "org.scalatestplus.play" %% "scalatestplus-play"  % "7.0.1"   % Test,
      "org.mockito"           %% "mockito-scala"         % "1.17.31" % Test,
      "com.opentable.components" % "otj-pg-embedded"    % "1.0.3"   % Test,
    ),
    // Disable unused warnings that Play generates
    scalacOptions ++= Seq(
      "-Xfatal-warnings",
      "-Xlint:-unused,_",
      "-deprecation",
      "-feature",
    ),
    libraryDependencies += evolutions,
    Test / javaOptions += "-Dconfig.file=conf/application.test.conf",
    Test / fork := true,
  )
