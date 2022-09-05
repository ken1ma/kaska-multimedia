package jp.ken1ma.kaska.multimedia
package FFmpeg

import scala.util.Using
import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}
import java.nio.file.{Path, Files, StandardOpenOption}, StandardOpenOption.{CREATE, WRITE, TRUNCATE_EXISTING}
import java.nio.charset.StandardCharsets.UTF_8

import cats.syntax.all.*
import cats.effect.Async
import cps.async
import cps.monads.catsEffect.{*, given}
import fs2.Stream

import org.bytedeco.ffmpeg.avformat.AVStream
import org.bytedeco.ffmpeg.avcodec.{AVCodecContext, AVPacket}
import org.bytedeco.ffmpeg.avutil.{AVFrame, AVRational}
import org.bytedeco.ffmpeg.global.avformat.av_read_frame
import org.bytedeco.ffmpeg.global.avcodec.{avcodec_send_packet, avcodec_receive_frame, av_packet_unref}
import org.bytedeco.ffmpeg.global.avcodec.{avcodec_send_frame, avcodec_receive_packet}
import org.bytedeco.ffmpeg.global.avcodec.* // AV_CODEC_ID_H264, ..., AV_CODEC_ID_MJPEG, ...
import org.bytedeco.ffmpeg.global.avutil.*
import org.log4s.getLogger

import jp.ken1ma.kaska.Cps.syntax._ // await

import FFmpegCppHelper.*
import FFmpegFormatHelper.*
import FFmpegCodecHelper.*
import FFmpegFilterHelper.*
import FFmpegSwScaleHelper.*

object FFmpegStream:
  val log = getLogger

  case class DecodedFrame(frm: AVFrame, decodeCtx: CodecContext, stream: AVStream, fmtCtx: FormatContext)

class FFmpegStream[F[_]: Async] extends FFmpegFormatHelper[F]
    with FFmpegCodecHelper[F]
    with FFmpegFilterHelper[F]
    with FFmpegSwScaleHelper[F]:
  import FFmpegStream._

  def streamFrames(stream: AVStream, fmtCtx: FormatContext): Stream[F, DecodedFrame] = {
    import fmtCtx.fmt_ctx

    val codecpar = stream.codecpar

    // the codec for decoding
    openCodecFromParams(codecpar, logCtx = fmtCtx).flatMap { codecCtx =>
      import codecCtx.codec_ctx

      // memory for decoding
      allocateDecodeContext(logCtx = codecCtx).flatMap { decodeCtx =>
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

            println(s"streamFrames: avcodec_receive_frame")
            avcodec_receive_frame(codec_ctx, frm) match // calls av_frame_unref
              case -35 if (frame_number == 0) => // FIXME use AVERROR_EAGAIN
                // bbb_sunflower_2160p_60fps_normal.mp4: the first two calls return ENOMSG
                // ENOMSG = 35 // No message of the desired type (POSIX.1-2001)
                Stream.empty

              case ret if (ret < 0) =>
                log.warn(s"${logCtx.logName}: avcodec_receive_frame returned $ret")
                Stream.empty

              case ret =>
                //log.trace(s"${logCtx.logName}: pkt.pts = ${pkt.pts}, dts = ${pkt.dts}, duration = ${pkt.duration}")
                //log.trace(s"${logCtx.logName}: frm.pts = ${frm.pts}, pkt_duration = ${frm.pkt_duration}")

                ret match
                  case 0 =>
                  case ret => log.warn(s"${logCtx.logName}: avcodec_receive_frame returned $ret")
                frm.decode_error_flags match
                  case 0 =>
                  case decode_error_flags => log.warn(s"${logCtx.logName}: decode_error_flags = $decode_error_flags")

                // frm.display_picture_number seems to be always 0 for H264
                // but avcodec_receive_frame seems to return frames in the display order (as seen in pkt_pts)

                Stream.emit(DecodedFrame(frm, codecCtx, stream, fmtCtx))

          } else {
            av_packet_unref(pkt)
            Stream.empty
          }
        }
      }
    }
  }

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
                //log.trace(s"elapsedTime = $elapsedTime, ptsDiffMs = $ptsDiffMs (${frm.pts}, ${frm.time_base.num} / ${frm.time_base.den}) (${stream.start_time}, ${stream.time_base.num} / ${stream.time_base.den})")
                (Some(firstTime), (Option.when(elapsedTime < ptsDiffMs)(ptsDiffMs - elapsedTime), Frame(frm, stream)))

          }.flatMap { case (_, (delayOpt, elem)) =>
            delayOpt match
              case Some(delay) => Stream.emit(elem).delayBy(FiniteDuration(delay, MILLISECONDS))
              case None        => Stream.emit(elem)
          }
        }
      }
*/

  object FileWrite:
    def h264(out: Path, width: Int, height: Int): Stream[F, AVFrame => Stream[F, Unit]] =
      h264(out, codec_ctx => {
        // Avoid `The encoder timebase is not set.`
        codec_ctx.time_base.num(1)
        codec_ctx.time_base.den(60000)
        // Avoid `Specified pixel format -1 is invalid or not supported`
        codec_ctx.pix_fmt(AV_PIX_FMT_YUV420P)
        // Avoid `dimensions not set`
        codec_ctx.width(width)
        codec_ctx.height(height)
      })

    def h264(out: Path, customizeContext: AVCodecContext => Unit = _ => ()): Stream[F, AVFrame => Stream[F, Unit]] =
      val logCtx = SimpleLogContext(out.toString)
      Stream.fromAutoCloseable(Async[F].delay { Files.newByteChannel(out,
          CREATE, WRITE, TRUNCATE_EXISTING) }).flatMap { outCh =>

        // the codec for encoding
        openCodec(AV_CODEC_ID_H264, logCtx, customizeContext).flatMap { codecCtx =>
          import codecCtx.codec_ctx

          // memory for encoding
          allocateEncodeContext(logCtx = codecCtx).flatMap { encodeCtx =>
            import encodeCtx.pkt

            Stream.emit(frm => Stream.eval(Async[F].delay {
              println(s"FileWrite: avcodec_send_frame")
              avcodec_send_frame(codec_ctx, frm)
                  .throwWhen(_ < 0, s"avcodec_send_frame failed", log, logCtx)
              av_frame_unref(frm) // TODO is this correct?

              println(s"FileWrite: avcodec_receive_packet")
              avcodec_receive_packet(codec_ctx, pkt)
                  .throwWhen(_ < 0, s"avcodec_receive_packet failed", log, logCtx)

              var data = pkt.data
              val size = pkt.size
              data = data.limit(size) // override (ffmpeg-platform:5.0-1.5.7: position, limit, capacity are all zero)

              log.trace(f"${out.toAbsolutePath}: appending ($size%,d bytes)")
              outCh.write(data.asByteBuffer)
            }))
          }
        }
      }

    def aac(out: Path, srcStream: AVStream): Stream[F, AVFrame => Stream[F, Unit]] =
      aac(out, codec_ctx => {
        // Avoid `The encoder timebase is not set.`
        codec_ctx.time_base.num(1)
        codec_ctx.time_base.den(srcStream.codecpar.sample_rate)
        // Avoid `Specified sample format -1 is invalid or not supported`
        codec_ctx.sample_fmt(AV_SAMPLE_FMT_FLTP) // wav: srcStream.codecpar.format results in `Specified sample format s16 is invalid or not supported`
        // Avoid `Specified sample rate 0 is not supported`
        codec_ctx.sample_rate(srcStream.codecpar.sample_rate)
        // Avoid `Unsupported channel layout "0 channels"`
        codec_ctx.channel_layout(AV_CH_LAYOUT_MONO) // FIXME AV_CH_LAYOUT_STEREO results in segfault

        codec_ctx.bit_rate(srcStream.codecpar.bit_rate)
      })

    def aac(out: Path, customizeContext: AVCodecContext => Unit = _ => ()): Stream[F, AVFrame => Stream[F, Unit]] =
      val logCtx = SimpleLogContext(out.toString)
      Stream.fromAutoCloseable(Async[F].delay { Files.newByteChannel(out,
          CREATE, WRITE, TRUNCATE_EXISTING) }).flatMap { outCh =>

        // the codec for encoding
        // https://github.com/FFmpeg/FFmpeg/blob/master/doc/examples/transcode_aac.c
        openCodec(AV_CODEC_ID_AAC, logCtx, customizeContext).flatMap { codecCtx =>
          import codecCtx.codec_ctx

          // memory for encoding
          allocateEncodeContext(logCtx = codecCtx).flatMap { encodeCtx =>
            import encodeCtx.pkt

            Stream.emit(frm => Stream.eval(Async[F].delay {
              avcodec_send_frame(codec_ctx, frm)
                  .throwWhen(_ < 0, s"avcodec_send_frame failed", log, logCtx)
              av_frame_unref(frm) // TODO is this correct?

              avcodec_receive_packet(codec_ctx, pkt) match
                case AVERROR_EAGAIN =>
                  //println(s"#### AVERROR_EAGAIN")

                case AVERROR_EINVAL =>
                  println(s"#### AVERROR_EINVAL")
                  //throw FFmpegException(encodeCtx)(s"avcodec_receive_packet: encoding error")

                case ret =>
                  println(s"#### ret = $ret")
                  ret.throwWhen(_ < 0, s"avcodec_receive_packet failed", log, logCtx)

                  var data = pkt.data
                  val size = pkt.size
                  data = data.limit(size) // override (ffmpeg-platform:5.0-1.5.7: position, limit, capacity are all zero)

                  log.trace(f"${out.toAbsolutePath}: appending ($size%,d bytes)")
                  outCh.write(data.asByteBuffer)

                  //println(s"#### one more ${avcodec_receive_packet(codec_ctx, pkt)}")
            }))
          }
        }
      }

  object FrameFileGen:
    def jpeg(dir: Path, width: Int, height: Int): Stream[F, AVFrame => Stream[F, Path]] =
      if (!Files.isDirectory(dir))
        Files.createDirectories(dir)

      // the codec for encoding
      openCodec(AV_CODEC_ID_MJPEG, SimpleLogContext(dir.toString), codec_ctx => {
            // Avoid `The encoder timebase is not set.`
            codec_ctx.time_base.num(1)
            codec_ctx.time_base.den(1)
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
          Stream.emit(writeFrame(codec_ctx, encodeCtx, (frm, pkt) => dir.resolve(s"pts=${pkt.pts}.jpeg"))) // TODO customize pathGen
        }
      }

    def png(dir: Path, width: Int, height: Int): Stream[F, AVFrame => Stream[F, Path]] = 
      if (!Files.isDirectory(dir))
        Files.createDirectories(dir)

      // FIXME the image is repeated three times; probably need to convert frm to AV_PIX_FMT_RGB24

      // the codec for encoding
      openCodec(AV_CODEC_ID_PNG, SimpleLogContext(dir.toString), codec_ctx => {
            // Avoid `The encoder timebase is not set.`
            codec_ctx.time_base.num(1)
            codec_ctx.time_base.den(1)
            // Avoid `Specified pixel format -1 is invalid or not supported`
            codec_ctx.pix_fmt(AV_PIX_FMT_RGB24) // AV_PIX_FMT_YUV420P results in `Specified pixel format yuv420p is invalid or not supported`
            // Avoid `dimensions not set`
            codec_ctx.width(width)
            codec_ctx.height(height)
          }).flatMap { codecCtx =>
        import codecCtx.codec_ctx

        // memory for encoding
        allocateEncodeContext(logCtx = codecCtx).flatMap { encodeCtx =>
          Stream.emit(writeFrame(codec_ctx, encodeCtx, (frm, pkt) => dir.resolve(s"pts=${pkt.pts}.png"))) // TODO customize pathGen
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
      log.info(f"writing $file ($size%,d bytes)")
      Files.write(file, buf)

      Stream.emit(file)


  def scaleFrame(dstFrm: AVFrame, srcFrm: AVFrame, swScaleCtx: SwScaleContext): Stream[F, Unit] =
      Stream.emit { swScaleCtx.scaleFrame(dstFrm, srcFrm) }

  def scaleFrame(srcFrm: AVFrame, swScaleCtx: SwScaleContextWithDstFrm): Stream[F, Unit] =
      Stream.emit { swScaleCtx.scaleFrame(srcFrm) }
