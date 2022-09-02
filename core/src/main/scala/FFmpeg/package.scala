package jp.ken1ma.kaska.multimedia

import org.log4s.Logger

package object FFmpeg {
  // The logs have hierarchical name such as `file (codec): ...`
  trait LogContext {
    def logName: String

    /** name to be shown to the user (exclude security sensitive information) */
    def msgName: String

    def childLogParen(suffix: String): LogContext =
        SimpleLogContext(s"$logName ($suffix)", s"$msgName ($suffix)")

    def childLogColon(suffix: String): LogContext =
        SimpleLogContext(s"$logName: $suffix", s"$msgName: $suffix")
  }

  class SimpleLogContext(val logName: String, val msgName: String) extends LogContext
  object SimpleLogContext {
    def apply(logName: String, msgName: String): LogContext = new SimpleLogContext(logName, msgName)
    def apply(name: String): LogContext = SimpleLogContext(name, name)
  }

  extension (log: Logger)
    def error(logCtx: LogContext)(msg: String): Unit = log.error(s"${logCtx.logName}: $msg")
    def warn (logCtx: LogContext)(msg: String): Unit = log.warn (s"${logCtx.logName}: $msg")
    def info (logCtx: LogContext)(msg: String): Unit = log.info (s"${logCtx.logName}: $msg")
    def debug(logCtx: LogContext)(msg: String): Unit = log.debug(s"${logCtx.logName}: $msg")
    def trace(logCtx: LogContext)(msg: String): Unit = log.trace(s"${logCtx.logName}: $msg")

  // Throwable(message) leaves cause == this
  // https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/lang/Throwable.java#L198
  // thus we cannot say `class FFmpegException(message: String, cause: Throwable = null) extends RuntimeException(message, cause)`
  // to remain compatible
  class FFmpegException(message: String) extends RuntimeException(message)
  object FFmpegException {
    def apply(message: String, cause: Throwable): FFmpegException = {
      val ex = new FFmpegException(message)
      ex.initCause(cause)
      ex
    }

    def apply(logCtx: LogContext)(message: String): FFmpegException =
        new FFmpegException(s"${logCtx.msgName}: $message")

    def apply(logCtx: LogContext)(message: String, cause: Throwable): FFmpegException =
        apply(s"${logCtx.msgName}: $message", cause)
  }
}
