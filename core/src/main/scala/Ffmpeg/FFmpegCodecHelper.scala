package jp.ken1ma.kaska.multimedia
package Ffmpeg

import java.nio.charset.StandardCharsets.UTF_8

import org.bytedeco.ffmpeg.avcodec.{AVCodecContext, AVCodec, AVCodecParameters, AVPacket}
import org.bytedeco.ffmpeg.global.avcodec._
import org.bytedeco.ffmpeg.global.avutil.{av_frame_alloc, av_frame_free}
import org.bytedeco.javacpp.PointerPointer
import org.log4s.getLogger

import FFmpegCppHelper._

object FFmpegCodecHelper {
  val log = getLogger

  case class CodecContext(
    codec_ctx: AVCodecContext,
    codec: AVCodec,
    logCtx: LogContext,
  ) extends LogContext with AutoCloseable {
    log.trace(s"creating CodecContext")

    def close(): Unit = {
      log.trace(s"destroying CodecContext")
      avcodec_close(codec_ctx)
      avcodec_free_context(codec_ctx)
    }

    def logName = s"${logCtx.logName} (${codec.name.getString(UTF_8)})"
    def msgName = s"${logCtx.msgName} (${codec.long_name.getString(UTF_8)})"
  }

  object CodecContext {
    def fromCodecParams(params: AVCodecParameters,
        logCtx: LogContext, customizeContext: AVCodecContext => Unit = _ => ()): CodecContext = {
      val codec_ctx = avcodec_alloc_context3(null)

      var succeeded = false
      try {
        avcodec_parameters_to_context(codec_ctx, params)
            .throwWhen(_ < 0, s"avcodec_parameters_to_context failed", log, logCtx)

        customizeContext(codec_ctx)

        import codec_ctx.codec_id
        val codec = avcodec_find_decoder(codec_id)
        if (codec == null)
          throw new FFmpegException(s"${logCtx.msgName}: avcodec_find_decoder failed: not found: $codec_id")

        avcodec_open2(codec_ctx, codec, null.asInstanceOf[PointerPointer[_]])
            .throwWhen(_ < 0, s"avcodec_open2 failed", log, logCtx)

        succeeded = true
        CodecContext(codec_ctx, codec, logCtx)

      } finally {
        if (!succeeded)
          avcodec_free_context(codec_ctx)
      }
    }

    def fromCodec(codec_id: Int,
        logCtx: LogContext, customizeContext: AVCodecContext => Unit = _ => ()): CodecContext = {
      val codec = avcodec_find_encoder(codec_id)
      if (codec == null)
          throw new FFmpegException(s"${logCtx.msgName}: avcodec_find_encoder failed")

      val codec_ctx = avcodec_alloc_context3(codec)
      if (codec_ctx == null)
          throw new FFmpegException(s"${logCtx.msgName}: avcodec_alloc_context3 failed")

      var succeeded = false
      try {
        customizeContext(codec_ctx)

        avcodec_open2(codec_ctx, codec, null.asInstanceOf[PointerPointer[_]])
            .throwWhen(_ < 0, s"${logCtx.msgName}: avcodec_open2 failed", log, logCtx)

        succeeded = true
        CodecContext(codec_ctx, codec, logCtx)

      } finally {
        if (!succeeded)
          avcodec_free_context(codec_ctx)
      }
    }

    def aac(logCtx: LogContext, customizeContext: AVCodecContext => Unit = _ => ()): CodecContext =
      fromCodec(AV_CODEC_ID_AAC, logCtx, customizeContext)
  }

  case class DecodeContext(
    logCtx: LogContext,
  ) extends LogContext with AutoCloseable {
    log.trace(s"creating DecodeContext")
    val pkt: AVPacket = av_packet_alloc()
    if (pkt == null)
      throw new FFmpegException(s"${logCtx.msgName}: av_packet_alloc failed")

    val frm = av_frame_alloc()
    if (frm == null)
      throw new FFmpegException(s"${logCtx.msgName}: av_frame_alloc failed")

    def close(): Unit = {
      log.trace(s"destroying DecodeContext")
      av_frame_free(frm)
      av_packet_free(pkt)
    }

    def logName = logCtx.logName
    def msgName = logCtx.msgName
  }

  case class EncodeContext(
    logCtx: LogContext,
  ) extends LogContext with AutoCloseable {
    log.trace(s"creating EncodeContext")
    val pkt: AVPacket  = av_packet_alloc()
    if (pkt == null)
      throw new FFmpegException(s"${logCtx.msgName}: av_packet_alloc failed")

    def close(): Unit = {
      log.trace(s"destroying EncodeContext")
      av_packet_free(pkt)
    }

    def logName = logCtx.logName
    def msgName = logCtx.msgName
  }
}
