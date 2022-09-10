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

The program to run is constructed by

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

    1. `--in=text` will be `in = Path.of(s"$text")`
    1. `--out=text` will be `out = Path.of(s"$text")`

3. `-e` is is a raw Scala expression, except

    1. `name = value` becomes `val name = value`
        1. When it starts with `name =`, `val ` is inserted at the beginning

    1. TODO: expr { name =>

    1. TODO: Multiple expressions will be connected by `flatMap`?


## Examples

1. Extract video frames

    1. All the frames as JPEG files

            tool/run run -i "$HOME/Downloads/Record of Lodoss War Opening [HD]-kagzOJsHBg4.mp4" -o "out/Lodoss-allFrames" -e "readFile(in).flatMap { fmtCtx =>" -e "stream = fmtCtx.videoStreams.head" -e "FrameFileGen.jpeg(dir = out, stream.width, stream.height).flatMap { fileGen =>" -e "streamFrames(stream, fmtCtx).map(_.frm).flatMap(fileGen)" --show-scala

    1. The key frames as PNG files

            tool/run run -i "$HOME/Downloads/Record of Lodoss War Opening [HD]-kagzOJsHBg4.mp4" -o "out/Lodoss-keyFrames" -e "readFile(in, dump = true).flatMap { fmtCtx =>" -e "stream = fmtCtx.videoStreams.head" -e "FrameFileGen.png(dir = out, stream.width, stream.height).flatMap { fileGen =>" -e "streamFrames(stream, fmtCtx).map(_.frm).filter(_.keyFrame).flatMap(fileGen)" --show-scala

1. Transcode a video to H264

        tool/run run -i "$HOME/Downloads/Record of Lodoss War Opening [HD]-kagzOJsHBg4.mp4" -o "out/Lodoss.h264" -e "readFile(in).flatMap { fmtCtx =>" -e "stream = fmtCtx.videoStreams.head" -e "FileWrite.h264(out, stream.width, stream.height).flatMap { fileWrite =>" -e "streamFrames(stream, fmtCtx).map(_.frm).flatMap(fileWrite)" --show-scala

    1. Skip even frames (frame_number starts at 1)

            tool/run run -i "$HOME/Downloads/Record of Lodoss War Opening [HD]-kagzOJsHBg4.mp4" -o "out/Lodoss-oddFrames.h264" -e "readFile(in).flatMap { fmtCtx =>" -e "stream = fmtCtx.videoStreams.head" -e "FileWrite.h264(out, stream.width, stream.height).flatMap { fileWrite =>" -e "streamFrames(stream, fmtCtx).filter(_.decodeCtx.codec_ctx.frame_number % 2 == 1).map(_.frm).flatMap(fileWrite)" --show-scala

    1. Halve the dimension by swscale

            tool/run run -i "$HOME/Downloads/Record of Lodoss War Opening [HD]-kagzOJsHBg4.mp4" -o "out/Lodoss-scaled.h264" -e "readFile(in).flatMap { fmtCtx =>" -e "stream = fmtCtx.videoStreams.head" -e "FileWrite.h264(out, stream.width / 2, stream.height / 2).flatMap { fileWrite =>" -e "allocSwScaleContextWithDstFrm(stream.width, stream.height, AV_PIX_FMT_YUV420P, stream.width / 2, stream.height / 2, AV_PIX_FMT_YUV420P, logCtx = fmtCtx).flatMap { swScaleCtx =>" -e "streamFrames(stream, fmtCtx).map(_.frm).flatMap(srcFrm => scaleFrame(srcFrm, swScaleCtx)).flatMap(_ => fileWrite(swScaleCtx.dstFrm))" --show-scala

    1. Halve the dimension by filter (FIXME: often times crash, completed at least once as expected)

            tool/run run -i "$HOME/Downloads/Record of Lodoss War Opening [HD]-kagzOJsHBg4.mp4" -o "out/Lodoss-filtered.h264" -e "readFile(in).flatMap { fmtCtx =>" -e "stream = fmtCtx.videoStreams.head" -e "FileWrite.h264(out, stream.width / 2, stream.height / 2).flatMap { fileWrite =>" -e "allocFilter(s\\u0022scale=\${stream.width / 2}:\${stream.height / 2},drawtext=text='%{pts}':fontfile=data/font/ipaexg.ttf:fontsize=10:fontcolor=red:x=w-tw-10:y=h-th-10\\u0022, stream.width, stream.height, AV_PIX_FMT_YUV420P, stream.time_base.num, stream.time_base.den, stream.sample_aspect_ratio.num, stream.sample_aspect_ratio.den, parentLogCtx = fmtCtx).flatMap { filterCtx =>" -e "streamFrames(stream, fmtCtx).map(_.frm).flatMap(srcFrm => filterCtx.filterFrame(srcFrm, fileWrite))" --show-scala

1. Transcode an audio to aac

        tool/run run -i "$HOME/Documents/convivial/Moomin.wav" -o "out/Moomin.aac" -e "readFile(in).flatMap { fmtCtx =>" -e "stream = fmtCtx.audioStreams.head" -e "FileWrite.aac(out, stream).flatMap { fileWrite =>" -e "streamFrames(stream, fmtCtx).flatMap(fileWrite)"


## More SBT commands

1. Run the unit tests

		test

2. Delete the built files

		clean

3. Search for dependency updates

        dependencyUpdates

4. Show the dependencies

        dependencyTree


## References

1. https://github.com/FFmpeg/FFmpeg/blob/master/doc/examples/
1. https://ffmpeg.org/doxygen/5.0/
1. https://javadoc.io/doc/org.bytedeco/javacpp/latest/index.html
1. https://javadoc.io/doc/org.bytedeco/ffmpeg/latest/index.html
1. https://www.ffmpeg.org/ffmpeg-filters.html#drawtext-1
