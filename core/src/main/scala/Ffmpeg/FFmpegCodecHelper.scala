package jp.ken1ma.kaska.multimedia
package Ffmpeg

import java.nio.charset.StandardCharsets.UTF_8

import cats.syntax.all.*
import cats.effect.Async
import cps.async
import cps.monads.catsEffect.{*, given}
import fs2.Stream

import org.bytedeco.ffmpeg.avcodec.{AVCodecContext, AVCodec, AVCodecParameters, AVPacket}
import org.bytedeco.ffmpeg.global.avcodec.*
import org.bytedeco.ffmpeg.global.avutil.{av_frame_alloc, av_frame_free}
import org.bytedeco.javacpp.PointerPointer
import org.log4s.getLogger

import FFmpegCppHelper.*

object FFmpegCodecHelper:
  val log = getLogger

  case class CodecContext(
    codec_ctx: AVCodecContext,
    codec: AVCodec,
    logCtx: LogContext,
  ) extends LogContext with AutoCloseable {
    log.trace(this)("open")

    def close(): Unit = {
      log.trace(this)("close")
      avcodec_close(codec_ctx)
      avcodec_free_context(codec_ctx)
    }

    def logName = s"${logCtx.logName} (${codec.name.getString(UTF_8)})"
    def msgName = s"${logCtx.msgName} (${codec.long_name.getString(UTF_8)})"
  }

  case class DecodeContext(
    logCtx: LogContext,
  ) extends LogContext with AutoCloseable {
    log.trace(this)("allocate")
    val pkt: AVPacket = av_packet_alloc()
    if (pkt == null)
      throw FFmpegException(this)(s"av_packet_alloc failed")

    val frm = av_frame_alloc()
    if (frm == null)
      throw FFmpegException(this)(s"av_frame_alloc failed")

    def close(): Unit = {
      log.trace(this)("release")
      av_frame_free(frm)
      av_packet_free(pkt)
    }

    def logName = s"${logCtx.logName}: decode"
    def msgName = s"${logCtx.logName}: decode"
  }

  case class EncodeContext(
    logCtx: LogContext,
  ) extends LogContext with AutoCloseable {
    log.trace(this)("allocate")
    val pkt: AVPacket  = av_packet_alloc()
    if (pkt == null)
      throw FFmpegException(this)(s"av_packet_alloc failed")

    def close(): Unit = {
      log.trace(this)("release")
      av_packet_free(pkt)
    }

    def logName = s"${logCtx.logName}: encode"
    def msgName = s"${logCtx.logName}: encode"
  }

trait FFmpegCodecHelper[F[_]: Async]:
  import FFmpegCodecHelper.*

  def openCodecFromParams(params: AVCodecParameters,
      logCtx: LogContext, customizeContext: AVCodecContext => Unit = _ => ()): Stream[F, CodecContext] = {
    val codec_ctx = avcodec_alloc_context3(null)

    var succeeded = false
    try {
      avcodec_parameters_to_context(codec_ctx, params)
          .throwWhen(_ < 0, s"avcodec_parameters_to_context failed", log, logCtx)

      customizeContext(codec_ctx)

      import codec_ctx.codec_id
      val codec = avcodec_find_decoder(codec_id)
      if (codec == null)
        throw FFmpegException(logCtx)(s"avcodec_find_decoder failed: not found: $codec_id")

      avcodec_open2(codec_ctx, codec, null.asInstanceOf[PointerPointer[_]])
          .throwWhen(_ < 0, s"avcodec_open2 failed", log, logCtx)

      succeeded = true
      Stream.fromAutoCloseable(Async[F].delay { new CodecContext(codec_ctx, codec, logCtx) })

    } finally {
      if (!succeeded)
        avcodec_free_context(codec_ctx)
    }
  }

  def openCodec(codec_id: Int,
      logCtx: LogContext, customizeContext: AVCodecContext => Unit = _ => ()): Stream[F, CodecContext] = {
    val codec = avcodec_find_encoder(codec_id)
    if (codec == null)
        throw FFmpegException(logCtx)(s"avcodec_find_encoder failed")

    val codec_ctx = avcodec_alloc_context3(codec)
    if (codec_ctx == null)
        throw FFmpegException(logCtx)(s"avcodec_alloc_context3 failed")

    var succeeded = false
    try {
      customizeContext(codec_ctx)

      avcodec_open2(codec_ctx, codec, null.asInstanceOf[PointerPointer[_]])
          .throwWhen(_ < 0, s"avcodec_open2 failed", log, logCtx)

      succeeded = true
      Stream.fromAutoCloseable(Async[F].delay { new CodecContext(codec_ctx, codec, logCtx) })

    } finally {
      if (!succeeded)
        avcodec_free_context(codec_ctx)
    }
  }

  def allocateDecodeContext(logCtx: LogContext) = Stream.fromAutoCloseable(Async[F].delay { new DecodeContext(logCtx) })

  def allocateEncodeContext(logCtx: LogContext) = Stream.fromAutoCloseable(Async[F].delay { new EncodeContext(logCtx) })
