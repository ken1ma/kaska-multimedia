package jp.ken1ma.kaska.multimedia
package Ffmpeg

import scala.util.Using
import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}
import java.nio.file.{Path, Files}
import java.nio.charset.StandardCharsets.UTF_8

import cats.syntax.all._
import cats.effect._
import cps.async
import cps.monads.catsEffect.{*, given}
import fs2.Stream

import org.bytedeco.ffmpeg.avcodec.{AVCodecContext, AVPacket}
import org.bytedeco.ffmpeg.avformat.AVStream
import org.bytedeco.ffmpeg.avutil.{AVFrame, AVRational}
import org.bytedeco.javacpp.{PointerPointer, BytePointer, DoublePointer}

import org.bytedeco.ffmpeg.global.avformat._
import org.bytedeco.ffmpeg.global.avcodec._
import org.bytedeco.ffmpeg.global.avutil._

import org.log4s.getLogger

import jp.ken1ma.kaska.Cps.syntax._ // await

import FFmpegCppHelper._
import FFmpegFormatHelper._
import FFmpegCodecHelper._

object FFmpegStream:
  val log = getLogger

  case class Frame(frame: AVFrame, stream: AVStream)

class FFmpegStream[F[_]: Async]:
  import FFmpegStream._

  val F = Async[F]

  def streamFrames(stream: AVStream, fmtCtx: FormatContext): Stream[F, AVFrame] = {
    import fmtCtx.fmt_ctx

    val codecpar = stream.codecpar

    // the codec for decoding
    Stream.fromAutoCloseable
        (F.delay { CodecContext.fromCodecParams(codecpar, logCtx = fmtCtx) })
        .flatMap { codecCtx =>
      import codecCtx.codec_ctx

      // memory for decoding
      Stream.fromAutoCloseable
          (F.delay { DecodeContext(logCtx = codecCtx) })
          .flatMap { decodeCtx =>
        import decodeCtx.{pkt, frm}

        /*val unmetered =*/ Stream.fromBlockingIterator(Iterator.continually {
          av_read_frame(fmt_ctx, pkt) // returned packet is reference-counted
        }.takeWhile(_ >= 0), chunkSize = 1).flatMap { _ =>
          if (pkt.stream_index == stream.index) {
            val frame_number = codec_ctx.frame_number // total number of frames returned from the decoder so far
            val logCtx = decodeCtx.childLogColon(frame_number.toString) 

            avcodec_send_packet(codec_ctx, pkt)
                .throwWhen(_ < 0, s"avcodec_send_packet failed", log, logCtx)
            av_packet_unref(pkt)

            avcodec_receive_frame(codec_ctx, frm) match // calls av_frame_unref
              case -35 if (frame_number == 0) =>
                // bbb_sunflower_2160p_60fps_normal.mp4: the first two calls return ENOMSG
                // ENOMSG = 35 // No message of the desired type (POSIX.1-2001)
                Stream.empty

              case ret if (ret < 0) =>
                log.warn(s"${logCtx.logName}: avcodec_receive_frame returned $ret")
                Stream.empty

              case ret =>
                log.trace(s"${logCtx.logName}: pkt.pts = ${pkt.pts}, dts = ${pkt.dts}, duration = ${pkt.duration}")
                log.trace(s"${logCtx.logName}: frm.pts = ${frm.pts}, pkt_duration = ${frm.pkt_duration}")

                ret match
                  case 0 =>
                  case ret => log.warn(s"${logCtx.logName}: avcodec_receive_frame returned $ret")
                frm.decode_error_flags match
                  case 0 =>
                  case decode_error_flags => log.warn(s"${logCtx.logName}: decode_error_flags = $decode_error_flags")

                // frm.display_picture_number seems to be always 0 for H264
                // but avcodec_receive_frame seems to return frames in the display order (as seen in pkt_pts)

                Stream.emit(frm)

          } else {
            av_packet_unref(pkt)
            Stream.empty
          }
        }
      }
    }
  }

  def streamVideoFramesFrom(file: Path): Stream[F, Frame] =
    log.info(f"streaming video: $file (${Files.size(file) / 1024.0 / 1024}%,.1fMB)")

    // open the file
    import FFmpegFormatHelper.FormatContext
    Stream.fromAutoCloseable
        (F.delay { FormatContext.openForRead(file, dump = true) })
        .flatMap { readCtx =>
      import readCtx.fmt_ctx

      // select the stream
      val (stream, streamIndex) = readCtx.selectStream(_.codecpar.codec_type == AVMEDIA_TYPE_VIDEO)
      val codecpar = stream.codecpar
      log.trace(s"resolution = ${codecpar.width}x${codecpar.height}")

      Stream.empty

/*
          // insert waits based on pts
          // ffmpeg-platform 5.0-1.5.7: frm.time_base is always 0/1 but stream.time_base is set
          unmetered.mapAccumulate(Option.empty[Long]) { case (firstTimeOpt, Frame(frm, stream)) =>
            val currentTime = System.currentTimeMillis
            firstTimeOpt match
              case None =>
                (Some(currentTime), (None, Frame(frm, stream)))

              case Some(firstTime) =>
                val ptsDiff = frm.pts - stream.start_time
                val ptsDiffMs = 1000 * ptsDiff * stream.time_base.num / stream.time_base.den

                val elapsedTime = currentTime - firstTime
                //log.debug(s"elapsedTime = $elapsedTime, ptsDiffMs = $ptsDiffMs (${frm.pts}, ${frm.time_base.num} / ${frm.time_base.den}) (${stream.start_time}, ${stream.time_base.num} / ${stream.time_base.den})")
                (Some(firstTime), (Option.when(elapsedTime < ptsDiffMs)(ptsDiffMs - elapsedTime), Frame(frm, stream)))

          }.flatMap { case (_, (delayOpt, elem)) =>
            delayOpt match
              case Some(delay) => Stream.emit(elem).delayBy(FiniteDuration(delay, MILLISECONDS))
              case None        => Stream.emit(elem)
          }
        }
      }
*/
    }

  object FrameFileGen:
    def jpeg(dir: Path, width: Int, height: Int): Stream[F, AVFrame => Stream[F, Path]] = 
      if (!Files.isDirectory(dir))
        Files.createDirectories(dir)

      // the codec for encoding
      Stream.fromAutoCloseable
          (F.delay { CodecContext.fromCodec(AV_CODEC_ID_MJPEG, SimpleLogContext(dir.toString), codec_ctx => {
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
          }) })
          .flatMap { codecCtx =>
        import codecCtx.codec_ctx

        // memory for decoding
        Stream.fromAutoCloseable
            (F.delay { EncodeContext(logCtx = codecCtx) })
            .flatMap { encodeCtx =>

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
