package jp.ken1ma.kaska.multimedia
package Ffmpeg

import java.nio.file.Path

import org.log4s.Logger

object FFmpegCppHelper {
  implicit class FFmpegIntRetOps(val ret: Int) extends AnyVal {
    def throwWhen(pred: Int => Boolean, msg: String, log: Logger, logCtx: LogContext): Int = {
      if (pred(ret)) {
        log.error(s"${logCtx.logName}: $msg ($ret)") // output the return value for easier troubleshooting
        throw new FFmpegException(s"${logCtx.msgName}: $msg") // don't leak the return value
      }
      ret
    }

/*
    def throwWhen(pred: Int => Boolean, msg: String, log: Logger): Int = {
      if (pred(ret)) {
        log.error(s"$msg ($ret)") // output the return value for easier troubleshooting
        throw new FFmpegException(msg) // don't leak the return value
      }
      ret
    }
*/
  }
}
