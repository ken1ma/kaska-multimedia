## Runtime environment

1. macOS / Linux / Windows

2. Java 17/11 LTS

    1. On macOS, [SDKMAN!](https://sdkman.io/) can be used to install one

            sdk install java 17.0.4-amzn


## Build environment

In addition to the runtime environment

1. [SBT](https://www.scala-sbt.org/) 1.7.1

	1. On macOS, [SDKMAN!](https://sdkman.io/) can be used to install one

			sdk install sbt

    2. On Windows [MSI installer](https://www.scala-sbt.org/1.x/docs/Installing-sbt-on-Windows.html)
        1. It might be older version but the correct version is automatically downloaded and used


# How to build and run

## Building and running locally

Start `sbt`

1. Build and run the tool

		tool/run

    1. Examples

            tool/run verify -in ../evip/out/ESA.pdf

2. Build the fat jar for distribution

		tool/assembly

	1. To run locally

			java -jar tool/target/scala-3.1.3/tool-assembly-0.1.0.jar


## The program

The program to run is constructed in by

1. The following imports are inserted at the beginning

        import java.nio.file._
        import scala.util._
        import scala.util.Properties.{userHome => HOME, userName => USER}
        import cats._
        import cats.syntax.all._
        import cats.effect._
        import cats.effect.syntax._
        import fs2._

        import jp.ken1ma.ffmpeg.FFmpegStream
        val ffmpegStream = FFmpegStream[IO]
        import ffmpegStream._

2. `-i` and `-o` are translated into value definitions

    1. `--in=text` will be `--val:in=Path.of(s"$text")`
    1. `--out=text` will be `--val:out=Path.of(s"$text")`

3. The value definitions are appended in the given order after the imports

    1. `--val=name=value` will be `val name = value`

4. The expressions are appended after the values

    1. Multiple expressions will be connected by `flatMap`


## Examples

1. Show the PTS

        tool/run run -i "$HOME/Downloads/Record of Lodoss War Opening [HD]-kagzOJsHBg4.mp4" --val "format = openForRead(in, dump = true)" --show-scala

1. Transcode wav to aac


1. Extract key frames from a video file

		tool/run run -i "$HOME/Downloads/Record of Lodoss War Opening [HD]-kagzOJsHBg4.mp4" -o "out/${in.baseName}-keyFrames/pts.jpeg" -d "write=JpegWriter(out)" -e "streamVideoFramesFrom(in).filter(_.frame.keyFrame).flatMap(write)"


## More SBT commands

1. Run the unit tests

		test

2. Delete the built files

		clean

3. Search for dependency updates

        dependencyUpdates

4. Show the dependencies

        dependencyTree
