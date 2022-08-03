package jp.ken1ma.kaska.multimedia
package tool
package command

import java.nio.file.Path

import cats.syntax.all._
import cats.effect.Async
import cps.async
import cps.monads.catsEffect.given
import fs2.Stream
import org.log4s.getLogger

import org.bytedeco.javacpp._
import org.bytedeco.skia._

import jp.ken1ma.kaska.multimedia.core._ // await

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

    import jp.ken1ma.kaska.multimedia.tool.command.RunHelper._

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
/*
    // This causes `ClassNotFoundException: rs$line$1`
    import dotty.tools.repl.ReplDriver
    val driver = ReplDriver(Array(
        "-classpath", "", // Avoid the default "."
        // avoid
        //  dotty.tools.dotc.MissingCoreLibraryException: Could not find package scala from compiler core libraries.
        //  Make sure the compiler core libraries are on the classpath.
        "-usejavacp")) 

    val classLoader = getClass.getClassLoader
    import dotty.tools.dotc.core.StdNames.str.{REPL_SESSION_LINE, REPL_RES_PREFIX}
    val vid = driver.initialState.valIndex

    val state = driver.run(s"$imports\n$script")(driver.initialState)

    val oid = state.objectIndex
    val result = Class.forName(s"$REPL_SESSION_LINE$oid", true, classLoader)
        .getDeclaredMethods.find(_.getName == s"$REPL_RES_PREFIX$vid")
        .map(_.invoke(null))
        .getOrElse(null)
*/

    import dotty.tools.repl.ScriptEngine
    val scriptEngine = new ScriptEngine
    val stream = scriptEngine.eval(s"$imports\n$script", null) // context is not being used in ScriptEngine
    val f = stream match
      case stream: Stream[F @unchecked, _] => stream.compile.drain
      case f: F[_] @unchecked => f.void
      case result => throw Exception(s"result is of type ${Option(result).map(_.getClass.getName).orNull}")
    f.await

    // TODO opts.showScalaFull
  }
