package jp.ken1ma

package object ffmpeg {
  trait LogContext {
    def logName: String

    /** name to be shown to the user (exclude security sensitive information) */
    def msgName: String

    def childLogParen(suffix: String): LogContext =
        SimpleLogContext(s"$logName ($suffix)", s"$msgName ($suffix)")

    def childLogColon(suffix: String): LogContext =
        SimpleLogContext(s"$logName: $suffix", s"$msgName: $suffix")
  }

  case class SimpleLogContext(logName: String, msgName: String) extends LogContext
  object SimpleLogContext {
    def apply(name: String): SimpleLogContext = SimpleLogContext(name, name)
  }

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
  }
}
