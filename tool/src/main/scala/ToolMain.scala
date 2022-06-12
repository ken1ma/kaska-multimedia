package jp.ken1ma.kaska.multimedia
package tool

import cats.effect.{IO, IOApp, ExitCode}
import cps.{async, await}
import cps.monads.catsEffect.given
import org.log4s.getLogger

//import core.Transcode

object ToolMain extends IOApp:
  val log = getLogger

  type F[A] = IO[A]

  def run(args: List[String]) = async[F] {
    import BuildInfo.{version, buildMinuteAtDefaultOffset}
    log.debug(s"version = $version ($buildMinuteAtDefaultOffset)")

    import ToolOptions.Commands._
    ToolOptions.parse(args) match
      case Some(command) =>
        // TODO log the JVM and OS

        command match
          case Transcode(out, ins) =>
            println(s"out = $out")
            ExitCode.Success

          case Version() =>
            println(s"Version ${BuildInfo.version} (build at ${BuildInfo.buildMinuteAtDefaultOffset})")
            ExitCode.Success

      case None =>
        ExitCode(0xff)
  }
