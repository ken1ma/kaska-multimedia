package jp.ken1ma.kaska.multimedia
package FFmpeg

import java.nio.charset.StandardCharsets.UTF_8

import cats.syntax.all.*
import cats.effect.Async
import cps.async
import cps.monads.catsEffect.{*, given}
import fs2.Stream

import org.bytedeco.ffmpeg.swscale.{SwsContext, SwsFilter}
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.swscale.*
import org.bytedeco.ffmpeg.global.avutil.{av_frame_alloc, av_frame_free}
import org.log4s.getLogger

import FFmpegCppHelper.*

object FFmpegSwScaleHelper:
  val log = getLogger

  trait SwScaleContext extends LogContext with AutoCloseable:
    def sws_ctx: SwsContext
    def logCtx: LogContext

    def scaleFrame(dstFrm: AVFrame, srcFrm: AVFrame): Unit =
      sws_scale_frame(sws_ctx, dstFrm, srcFrm)
          .throwWhen(_ < 0, s"sws_scale_frame failed", log, logCtx)

    log.trace(this)("open")

    def close(): Unit = {
      log.trace(this)("close")
      sws_freeContext(sws_ctx)
    }

    def logName = s"${logCtx.logName} (swscale)"
    def msgName = s"${logCtx.msgName} (swscale)"

  case class SwScaleContextMin(
    sws_ctx: SwsContext,
    logCtx: LogContext,
  ) extends SwScaleContext

  case class SwScaleContextWithDstFrm(
    sws_ctx: SwsContext,
    logCtx: LogContext,
  ) extends SwScaleContext:
    val dstFrm = av_frame_alloc()
    if (dstFrm == null)
      throw FFmpegException(this)(s"av_frame_alloc failed")

    def scaleFrame(srcFrm: AVFrame): Unit =
        scaleFrame(dstFrm, srcFrm)

    override def close(): Unit =
      super.close()
      av_frame_free(dstFrm)

trait FFmpegSwScaleHelper[F[_]: Async]:
  import FFmpegSwScaleHelper.*

  def allocSwScaleContext(srcWidth: Int, srcHeight: Int, srcFormat: Int,
      dstWidth: Int, dstHeight: Int, dstFormat: Int,
      flags: Int = SWS_BILINEAR,
      srcFilter: SwsFilter = null, dstFilter: SwsFilter = null,
      param: Array[Double] = null,
      logCtx: LogContext): Stream[F, SwScaleContext] =
    val sws_ctx = sws_getContext(srcWidth, srcHeight, srcFormat,
        dstWidth, dstHeight, dstFormat,
        flags, srcFilter, dstFilter, param)
      if (sws_ctx == null)
        throw FFmpegException(logCtx)(s"sws_getContext failed: ${srcWidth}x$srcHeight to ${dstWidth}x$dstHeight")

    var succeeded = false
    try
      succeeded = true
      Stream.fromAutoCloseable(Async[F].delay { SwScaleContextMin(sws_ctx, logCtx) })

    finally
      if !succeeded then
        sws_freeContext(sws_ctx)

  def allocSwScaleContextWithDstFrm(srcWidth: Int, srcHeight: Int, srcFormat: Int,
      dstWidth: Int, dstHeight: Int, dstFormat: Int,
      flags: Int = SWS_BILINEAR,
      srcFilter: SwsFilter = null, dstFilter: SwsFilter = null,
      param: Array[Double] = null,
      logCtx: LogContext): Stream[F, SwScaleContextWithDstFrm] =
    val sws_ctx = sws_getContext(srcWidth, srcHeight, srcFormat,
        dstWidth, dstHeight, dstFormat,
        flags, srcFilter, dstFilter, param)
      if (sws_ctx == null)
        throw FFmpegException(logCtx)(s"sws_getContext failed: ${srcWidth}x$srcHeight to ${dstWidth}x$dstHeight")

    var succeeded = false
    try
      succeeded = true
      Stream.fromAutoCloseable(Async[F].delay { SwScaleContextWithDstFrm(sws_ctx, logCtx) })

    finally
      if !succeeded then
        sws_freeContext(sws_ctx)
