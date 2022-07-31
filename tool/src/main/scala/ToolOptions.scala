package jp.ken1ma.kaska.multimedia
package tool

import scala.util.{Right, Left}

import cats.syntax.all._
import com.monovore.decline._
import org.log4s.getLogger

object ToolOptions:
  val log = getLogger

  object Commands:
    case class Run(exprs: Seq[String], vals: Seq[String], in: Option[String], out: Option[String], opts: RunOpts)
    case class RunOpts(
      showScala: Boolean,
      showScalaFull: Boolean,
    )
    case class Play(in: String)
    case class Version()
  import Commands._

  val exprs = Opts.options[String]("exression" , short = "e", metavar = "expression", help = "Evaluates the exression")
  val vals  = Opts.options[String]("definition", short = "d", metavar = "name=value", help = "Defines a value")
  val in    = Opts.option [String]("in"        , short = "i", metavar = "path"      , help = "Defines a value for the input file")
  val out   = Opts.option [String]("out"       , short = "o", metavar = "path"      , help = "Defines a value for the output file")

  val showScala     = Opts.flags("show-scala"     , help = "Shows the Scala code"     ).withDefault(0).map(_ > 0)
  val showScalaFull = Opts.flags("show-scala-full", help = "Shows the full Scala code").withDefault(0).map(_ > 0)

  val run = Opts.subcommand("run",
      "Constrcuts a program on video/audio and runs it") {
    (exprs.orEmpty, vals.orEmpty, in.orNone, out.orNone,
        (showScala, showScalaFull).tupled.map(RunOpts.apply)
    ).tupled.map(Run.apply)
  }

  val play = Opts.subcommand("play",
      "Plays video/audio") {
    in.map(Play.apply)
  }

  val version = Opts.subcommand("version",
      "Shows the version") {
    Opts(Version())
  }

  val command = Command(s"java -jar kaska-multimedia-tool-assembly-${BuildInfo.version}.jar", "Various operaions on media files") {
    run orElse play orElse version
  }

  def parse(args: List[String]) =
    log.debug(s"args = ${args.mkString(" ")}")

    command.parse(args) match
      case Right(command) => Some(command)
      case Left(help) =>
        Console.err.println(help.toString)
        None
