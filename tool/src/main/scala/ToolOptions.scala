package jp.ken1ma.kaska.multimedia
package tool

import scala.util.{Right, Left}
import java.nio.file.Path

import cats.syntax.all._
import com.monovore.decline._
import org.log4s.getLogger

object ToolOptions:
  val log = getLogger

  object Commands:
    case class Transcode(out: Path, in: Path)
    case class Version()
  import Commands._

  val out = Opts.option[Path]("out", metavar = "path", help = "Output video/image/audio path")
  val in = Opts.option[Path]("in", metavar = "path", help = "Input video/image/audio path")

  val transcode = Opts.subcommand("transcode",
      "Transcode video/image/audio files") {
    (out, in).tupled.map(Commands.Transcode.apply)
  }

  val version = Opts.subcommand("version",
      "Show the version") {
    Opts(Version())
  }

  val command = Command(s"java -jar kaska-multimedia-tool-assembly-${BuildInfo.version}.jar", "Various operaions on media files") {
    transcode orElse version
  }

  def parse(args: List[String]) =
    log.debug(s"args = ${args.mkString(" ")}")

    command.parse(args) match
      case Right(command) => Some(command)
      case Left(help) =>
        Console.err.println(help.toString)
        None
