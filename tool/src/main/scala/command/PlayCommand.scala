package jp.ken1ma.kaska.multimedia
package tool
package command

import java.nio.file.Path

import cats.effect.Async
import cps.{async, await}
import cps.monads.catsEffect.given
import org.log4s.getLogger

class PlayCommand[F[_]: Async]:
  def run(inFile: Path): F[Unit] = async[F] {
    println(s"inFile = $inFile")
  }
