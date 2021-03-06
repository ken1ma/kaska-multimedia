package jp.ken1ma.kaska.multimedia
package tool
package command

import java.nio.file.Path

import cats.effect.Async
import cps.{async, await}
import cps.monads.catsEffect.given
import org.log4s.getLogger

import org.bytedeco.javacpp._
import org.bytedeco.skia._
import org.bytedeco.skia.global.Skia._

import ToolOptions.Commands.RunOpts

class RunCommand[F[_]: Async]:
  def run(exprs: Seq[String], vals: Seq[String], in: Option[String], out: Option[String], opts: RunOpts): F[Unit] =
    def toVal(opt: Option[String], name: String): Seq[String] =
        opt.toSeq.map(text => s"""$name = Path.of(s"$text")""")
    run(exprs, toVal(in, "in") ++ toVal(out, "out") ++ vals, opts)

  def run(exprs: Seq[String], vals: Seq[String], opts: RunOpts): F[Unit] =
    def toExpr(text: String): String =
      text.indexOf('=') match
        case -1 => throw new IllegalArgumentException(s"Must contains '=': $text")
        case eq => s"val ${text.take(eq).trim} = ${text.drop(eq + 1).trim}"
    run(vals.map(toExpr) ++ exprs, opts)

  val imports = """
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
  """

  def run(exprs: Seq[String], opts: RunOpts): F[Unit] = async[F] {
    val script = exprs.mkString("\n")
    if (opts.showScala)
      println(script)

    // https://docs.scala-lang.org/scala3/reference/metaprogramming/staging.html
    // https://github.com/scala/scala3-staging.g8/blob/main/src/main/g8/src/main/scala/Main.scala
/*
    // "org.scala-lang" %% "scala3-staging" % scalaVersion.value,
    import scala.quoted._
    import scala.quoted.staging.{run, withQuotes, Compiler}
    given Compiler = Compiler.make(getClass.getClassLoader) // Needed to run or show quotes
    def code(using Quotes): Expr[_] = '{ println("Hello 世界") }
    println(s"code.show = ${withQuotes(code.show)}")
    val result = staging.run(code)
    println(s"result = $result")
*/

    // https://github.com/com-lihaoyi/Ammonite/blob/master/amm/compiler/src/main/scala-3/ammonite/compiler/Parsers.scala

    // https://github.com/lampepfl/dotty/blob/main/compiler/src/dotty/tools/repl/ReplDriver.scala
    // https://github.com/lampepfl/dotty/blob/main/compiler/src/dotty/tools/repl/ScriptEngine.scala
    // "org.scala-lang" %% "scala3-compiler" % scalaVersion.value,
    import dotty.tools.repl.ReplDriver
    val driver = ReplDriver(Array(
        "-classpath", "", // Avoid the default "."
        // avoid
        //  dotty.tools.dotc.MissingCoreLibraryException: Could not find package scala from compiler core libraries.
        //  Make sure the compiler core libraries are on the classpath.
        "-usejavacp")) 
    val state = driver.run(s"$imports\n$script")(driver.initialState)

    // TODO opts.showScalaFull
  }
