package jp.ken1ma.kaska.multimedia
package tool

import cats.effect.{IO, IOApp, ExitCode}
import cps.async
import cps.monads.catsEffect.given
import org.log4s.getLogger

import jp.ken1ma.kaska.multimedia.core._ // await

//import core.Transcode
import command._

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
          case Play(in) =>
            PlayCommand[F].run(in).await
            ExitCode.Success

          case Transcode(out, in) =>
            println(s"out = $out")
            ExitCode.Success

          case Version() =>
            println(s"Version ${BuildInfo.version} (build at ${BuildInfo.buildMinuteAtDefaultOffset})")
            ExitCode.Success

      case None =>
        ExitCode(0xff)
  }
