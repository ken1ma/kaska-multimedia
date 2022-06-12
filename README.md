## Runtime environment

1. Java 17/11 LTS
1. macOS / Linux / Windows


## Build environment

In addition to the runtime environment

1. [SBT](https://www.scala-sbt.org/) 1.6.2

	1. On macOS, [SDKMAN!](https://sdkman.io/) can be used to install one

			sdk install sbt

    1. On Windows [MSI installer](https://www.scala-sbt.org/1.x/docs/Installing-sbt-on-Windows.html)
        1. It might be older version but the correct version is automatically downloaded and used

1. Java 17

    1. On macOS, [SDKMAN!](https://sdkman.io/) can be used to install one

            sdk install java 17.0.3.6.1-amzn


# How to build and run

## Building and running locally

Start `sbt`

2. Build and run the tool

		tool/run

    1. Examples

            tool/run verify -in ../evip/out/ESA.pdf

4. Build the fat jar for distribution

		dist

	2. To run locally

			java -jar dist/kaska-multimedia-0.1.0.jar


## More SBT commands

1. Run the unit tests

		test

2. Delete the built files

		clean

3. Search for dependency updates

        dependencyUpdates

4. Show the dependencies

        dependencyTree

