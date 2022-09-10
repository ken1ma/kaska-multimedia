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

import jp.ken1ma.kaska.Cps.syntax._ // await

import ToolOptions.Commands.RunOpts

class RunCommand[F[_]: Async]:
  def run(exprs: Seq[String], in: Option[String], out: Option[String], opts: RunOpts): F[Unit] =
    def toVal(opt: Option[String], name: String, createParentDir: Boolean = false): Seq[String] =
        opt.toSeq.map(text => s"""$name = Path.of(s"$text")""") ++
        Option.when(createParentDir)(s"""Option($name.getParent).filter(!Files.isDirectory(_)).foreach(Files.createDirectories(_))""").toSeq
    run(toVal(in, "in") ++ toVal(out, "out", true) ++ exprs, opts)

  val imports = """
    import java.nio.file.*
    import java.time.*
    import scala.util.*
    import scala.util.Properties.{userHome => HOME, userName => USER}
    import cats.*
    import cats.syntax.all.*
    import cats.effect.*
    import cats.effect.syntax.*
    import fs2.*

    import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P

    import jp.ken1ma.kaska.multimedia.tool.command.RunHelper._

    import jp.ken1ma.kaska.multimedia.FFmpeg.FFmpegStream
  """

  val header = """
    |def program[F[_]: Async]: Stream[F, _] = {
    |  val ffmpegStream = FFmpegStream[F]
    |  import ffmpegStream.*
  """.stripMargin.trim

  val footer = """
    |// TODO Avoid `WARNING: Cats Effect global runtime already initialized; custom configurations will be ignored`
    |object MyApp extends IOApp.Simple:
    |  override def run = program[IO].compile.drain
    |MyApp.main(Array.empty)
  """.stripMargin.trim

  def run(exprs: Seq[String], opts: RunOpts): F[Unit] = async[F] {
    var indent = 1 // appends an extra `}` matching `{` of `program`
    val lines = exprs.map { expr =>
      // TODO expr.codePointStepper (handle code points rather than characters)
      val s0 = expr.trim // trim any character whose codepoint is less than or equal to 'U+0020' (the space character).
      var open = false
      val line = if s0.nonEmpty then
        if s0.endsWith("=>") then // should `{ name =>` be parsed?
          open = true
          s0
        else if Character.isJavaIdentifierStart(s0.head) then
          val (parts, s1) = s0.tail.span(Character.isJavaIdentifierPart)
          val name = s0.head +: parts
          val s2 = s1.trim
          if s2.nonEmpty && s2.head == '=' then
            val rhs = s2.tail.trim
            s"val $name = $rhs"
          else
            s0
        else
          s0
      else
        ""

      val indented = "  " * indent + line
      if open then
        indent += 1
      indented

    }
    val body = (lines ++ (indent - 1 to 0 by -1).map(i => "  " * i + "}")).mkString("\n")
    val script = s"$header\n$body\n$footer"
    if opts.showScala then
      println(script)

    // TODO opts.showScalaFull

    // https://docs.scala-lang.org/scala3/reference/metaprogramming/staging.html
    // https://github.com/scala/scala3-staging.g8/blob/main/src/main/g8/src/main/scala/Main.scala
    // "org.scala-lang" %% "scala3-staging" % scalaVersion.value,
/*can't figure out how to parse to Expr[T]
    import scala.quoted._
    import scala.quoted.staging.{run, withQuotes, Compiler}
    given Compiler = Compiler.make(getClass.getClassLoader) // Needed to run or show quotes
    def code(using Quotes): Expr[_] = '{ println("Hello 世界") }
    println(s"code.show = ${withQuotes(code.show)}")
    val result0 = staging.run(code)
    println(s"result0 = $result0")
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
    val result = scriptEngine.eval(s"$imports\n$script", null) // context (2nd argument) is not being used in ScriptEngine
/*
    val f = result match
      case stream: Stream[F @unchecked, _] => stream.compile.drain
      //case f: F[_] @unchecked => f.void
      case null => // null when there are no expressions (only vals)
         println(s"No result (null)") // specific message because this is a common mistake
         Async[F].unit
      case result =>
         println(s"The result is $result: ${result.getClass.getName}")
         Async[F].unit
    f.await
*/
  }
