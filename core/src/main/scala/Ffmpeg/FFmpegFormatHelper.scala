package jp.ken1ma.kaska.multimedia
package Ffmpeg

import java.nio.file.{Path, Files, StandardOpenOption}
import java.nio.charset.StandardCharsets.UTF_8

import cats.syntax.all._
import cats.effect.Async
import cps.async
import cps.monads.catsEffect.{*, given}
import fs2.Stream

import org.bytedeco.ffmpeg.avformat.{AVFormatContext, AVStream, AVInputFormat}
import org.bytedeco.ffmpeg.avcodec.{AVCodecContext, AVPacket}
import org.bytedeco.ffmpeg.avutil.{AVDictionary, AVFrame}
import org.bytedeco.ffmpeg.global.avformat._
import org.bytedeco.ffmpeg.global.avcodec._
import org.bytedeco.ffmpeg.global.avutil._
import org.bytedeco.javacpp.PointerPointer
import org.log4s.getLogger

import FFmpegCppHelper.*
import FFmpegCodecHelper.*

// TODO https://github.com/leandromoreira/ffmpeg-libav-tutorial seems to describe the sample in depth

object FFmpegFormatHelper:
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
        log.warn(this)(s"more than one candidate streams: ${candidates.map(_._2).mkString(", ")}")

      candidates.head
    }

    def close(): Unit = {
      log.debug(this)(s"close")
      avformat_close_input(fmt_ctx)
      avformat_free_context(fmt_ctx)
    }

    def logName = logCtx.logName
    def msgName = logCtx.msgName
  }

trait FFmpegFormatHelper[F[_]: Async] extends FFmpegCodecHelper[F]:
  import FFmpegFormatHelper.*

  /** @param fmt autodetected when null */
  def readFile(file: Path, fmt: AVInputFormat = null, options: AVDictionary = null, dump: Boolean = false): Stream[F, FormatContext] = {
    val logCtx1 = SimpleLogContext(file.toAbsolutePath.toString, file.getFileName.toString)
    log.debug(logCtx1)(f"open (${Files.size(file) / 1024.0 / 1024}%,.1fMB)")

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
        Stream.fromAutoCloseable(Async[F].delay { FormatContext(fmt_ctx, logCtx2) })

      } finally {
        if (!succeeded)
          avformat_close_input(fmt_ctx)
      }

    } finally {
      if (!succeeded)
        avformat_free_context(fmt_ctx)
    }
  }

  object FrameFileGen:
    def jpeg(dir: Path, width: Int, height: Int): Stream[F, AVFrame => Stream[F, Path]] = 
      if (!Files.isDirectory(dir))
        Files.createDirectories(dir)

      // the codec for encoding
      openCodec(AV_CODEC_ID_MJPEG, SimpleLogContext(dir.toString), codec_ctx => {
            // Avoid `The encoder timebase is not set.`
            codec_ctx.time_base().num(1)
            codec_ctx.time_base().den(1)
            // Avoid `Specified pixel format -1 is invalid or not supported`
            codec_ctx.pix_fmt(AV_PIX_FMT_YUV420P)
            // Avoid `dimensions not set`
            codec_ctx.width(width)
            codec_ctx.height(height)
            // Avoid `Non full-range YUV is non-standard, set strict_std_compliance to at most unofficial to use it`
            codec_ctx.strict_std_compliance(-1)
          }).flatMap { codecCtx =>
        import codecCtx.codec_ctx

        // memory for encoding
        allocateEncodeContext(logCtx = codecCtx).flatMap { encodeCtx =>
          Stream.emit(writeFrame(codec_ctx, encodeCtx, (frm, pkt) => dir.resolve(s"pts=${pkt.pts}.jpeg")))
        }
      }

  def writeFrame(codec_ctx: AVCodecContext, encodeCtx: EncodeContext, pathGen: (AVFrame, AVPacket) => Path)(frm: AVFrame): Stream[F, Path] =
    import encodeCtx.pkt
    val logCtx = encodeCtx // TODO can frm number be extracted from frm?

    avcodec_send_frame(codec_ctx, frm)
        .throwWhen(_ < 0, s"avcodec_send_frame failed", log, logCtx)
    av_frame_unref(frm) // TODO is this correct?

    avcodec_receive_packet(codec_ctx, pkt)
        .throwWhen(_ < 0, s"avcodec_receive_packet failed", log, logCtx)

    var data = pkt.data
    val size = pkt.size
    data = data.limit(size) // override (ffmpeg-platform:5.0-1.5.7: position, limit, capacity are all zero)
    val buf = new Array[Byte](size)
    data.asByteBuffer.get(buf)

    val file = pathGen(frm, pkt)
    log.info(f"writing $file (${buf.length}%,d bytes)")
    Files.write(file, buf)

    Stream.emit(file)
