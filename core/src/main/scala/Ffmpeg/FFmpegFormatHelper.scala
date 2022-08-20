package jp.ken1ma.kaska.multimedia
package Ffmpeg

import java.nio.file.{Path, Files, StandardOpenOption}
import java.nio.charset.StandardCharsets.UTF_8

import org.bytedeco.ffmpeg.avformat.{AVFormatContext, AVStream, AVInputFormat}
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.global.avformat._
import org.bytedeco.ffmpeg.global.avutil._
import org.bytedeco.javacpp.PointerPointer
import org.log4s.getLogger

import FFmpegCppHelper._

// TODO https://github.com/leandromoreira/ffmpeg-libav-tutorial seems to describe the sample in depth

object FFmpegFormatHelper {
  val log = getLogger

  case class FormatContext(
    fmt_ctx: AVFormatContext,
    logCtx: LogContext,
  ) extends LogContext with AutoCloseable {
    val streams: Seq[AVStream] = (0 until fmt_ctx.nb_streams).map(fmt_ctx.streams(_))

    def videoStreams: Seq[AVStream] = streams.filter(_.codecpar.codec_type == AVMEDIA_TYPE_VIDEO)
    def audioStreams: Seq[AVStream] = streams.filter(_.codecpar.codec_type == AVMEDIA_TYPE_AUDIO)

    /** @return stream and index */
    def selectStream(pred: AVStream => Boolean): (AVStream, Int) = {
      val candidates = (0 until fmt_ctx.nb_streams).map(i => (fmt_ctx.streams(i), i)).filter(pair => pred(pair._1))
      if (candidates.isEmpty)
        throw new FFmpegException(s"$msgName: no candidate stream")
      else if (candidates.size > 1)
        log.warn(s"$logName: more than one candidate streams: ${candidates.map(_._2).mkString(", ")}")

      candidates.head
    }

    def close(): Unit = {
      avformat_close_input(fmt_ctx)
      avformat_free_context(fmt_ctx)
    }

    def logName = logCtx.logName
    def msgName = logCtx.msgName
  }

  object FormatContext {
    /** @param fmt autodetected when null */
    def openForRead(file: Path, fmt: AVInputFormat = null, options: AVDictionary = null, dump: Boolean = false): FormatContext = {
      log.debug(s"opening $file")
      val logCtx1 = SimpleLogContext(file.toAbsolutePath.toString, file.getFileName.toString)

      val fmt_ctx = avformat_alloc_context()

      var succeeded = false
      try {
        val url = file.toString
        avformat_open_input(fmt_ctx, url, fmt, options)
            .throwWhen(_ < 0, s"avformat_open_input failed", log, logCtx1)

        try {
          val logCtx2 = logCtx1.childLogParen(fmt_ctx.iformat.name.getString(UTF_8))

          avformat_find_stream_info(fmt_ctx,
              null.asInstanceOf[PointerPointer[_]]) // options
              .throwWhen(_ < 0, s"avformat_find_stream_info failed", log, logCtx2)

          if (dump)
            av_dump_format(fmt_ctx,
                0, // stream index
                url,
                0) // input/output

          succeeded = true
          FormatContext(fmt_ctx, logCtx2)

        } finally {
          if (!succeeded)
            avformat_close_input(fmt_ctx)
        }

      } finally {
        if (!succeeded)
          avformat_free_context(fmt_ctx)
      }
    }
  }
}
