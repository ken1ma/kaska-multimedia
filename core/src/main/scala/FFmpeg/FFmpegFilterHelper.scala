package jp.ken1ma.kaska.multimedia
package FFmpeg

import java.nio.charset.StandardCharsets.UTF_8

import cats.syntax.all.*
import cats.effect.Async
import cps.async
import cps.monads.catsEffect.{*, given}
import fs2.Stream

import org.bytedeco.javacpp.{PointerPointer, BytePointer}
import org.bytedeco.ffmpeg.avfilter.{AVFilterContext, AVFilterGraph, AVFilterInOut}
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avfilter.*
import org.bytedeco.ffmpeg.global.avutil.{av_frame_alloc, av_frame_free, av_frame_unref}
import org.log4s.getLogger

import FFmpegCppHelper.*

// Based on https://github.com/FFmpeg/FFmpeg/blob/master/doc/examples/filtering_video.c

object FFmpegFilterHelper:
  val log = getLogger

  case class FilterContext(
    filter_graph: AVFilterGraph,
    buffersrc_ctx: AVFilterContext,
    buffersink_ctx: AVFilterContext,
    logCtx: LogContext,
  ) extends SimpleLogContext(logCtx.logName, logCtx.msgName) with AutoCloseable {
    log.trace(this)("open")

    val dstFrm = av_frame_alloc()
    if (dstFrm == null)
      throw FFmpegException(this)(s"av_frame_alloc failed")

 
    def close(): Unit = {
      log.trace(this)("close")
      //av_frame_free(dstFrm) // this results in SIGSEGV
      avfilter_graph_free(filter_graph)
      //avfilter_free(buffersink_ctx) // this results in SIGSEGV
      //avfilter_free(buffersrc_ctx) // this results in SIGSEGV
    }

    def filterFrame[F[_]: Async, A](srcFrm: AVFrame, f: AVFrame => Stream[F, A]): Stream[F, A] =
      // push the decoded frame into the filtergraph
      av_buffersrc_add_frame_flags(buffersrc_ctx, srcFrm, AV_BUFFERSRC_FLAG_KEEP_REF)
          .throwWhen(_ < 0, s"av_buffersrc_add_frame_flags failed", log, logCtx)

      // pull filtered frames from the filtergraph
      Stream.fromBlockingIterator(Iterator.continually {
        println(s"before av_buffersink_get_frame")
        val ret = av_buffersink_get_frame(buffersink_ctx, dstFrm)
            .throwWhen(_ < 0, s"av_buffersink_get_frame failed", log, logCtx)
        println(s"after av_buffersink_get_frame: $ret")
        ret
      }.takeWhile { _ match
        case `AVERROR_EAGAIN` => false
        case ret => true
      }, chunkSize = 1).flatMap { _ =>
        println(s"before f")
        f(dstFrm).map { result =>
          println(s"after f: $result")
          av_frame_unref(dstFrm)
          println(s"after av_frame_unref")
          result
        }
      }
  }

trait FFmpegFilterHelper[F[_]: Async]:
  import FFmpegFilterHelper.*

  def allocFilter(filterDescr: String,
      srcWidth: Int, srcHeight: Int, srcPixFmt: Int,
      timeBaseNum: Int, timeBaseDen: Int, pixAspectNum: Int, pixAspectDen: Int,
      parentLogCtx: LogContext): Stream[F, FilterContext] =
    val logCtx = parentLogCtx.childLogColon("filter")

    var succeeded = false
    try
      // Get filter definitions
      val buffersrc_def  = avfilter_get_by_name("buffer")
      if (buffersrc_def == null)
        throw FFmpegException(logCtx)(s"""avfilter_get_by_name("buffer") failed""")
      val buffersink_def = avfilter_get_by_name("buffersink")
      if (buffersink_def == null)
        throw FFmpegException(logCtx)(s"""avfilter_get_by_name("buffersink") failed""")

      // Allocate a single AVFilterInOut entry
      val inputs = avfilter_inout_alloc()
      if (inputs == null)
        throw FFmpegException(logCtx)(s"avfilter_inout_alloc failed")
      val outputs = avfilter_inout_alloc()
      if (outputs == null)
        throw FFmpegException(logCtx)(s"avfilter_inout_alloc failed")

      try
        // Allocate a filter graph
        val filter_graph = avfilter_graph_alloc()
        if (filter_graph == null)
          throw FFmpegException(logCtx)(s"avfilter_graph_alloc failed")

        // Create and add a filter instance into an existing graph
        // buffer video source: the decoded frames from the decoder will be inserted here
        val args = s"video_size=${srcWidth}x$srcHeight:pix_fmt=$srcPixFmt:time_base=$timeBaseNum/$timeBaseDen:pixel_aspect=$pixAspectNum/$pixAspectDen"
        val buffersrc_ctx = AVFilterContext()
        avfilter_graph_create_filter(buffersrc_ctx, buffersrc_def, "in",
            args, null, filter_graph)
            .throwWhen(_ < 0, s"avfilter_graph_create_filter(in) failed", log, logCtx)

        // buffer video sink: to terminate the filter chain
        val buffersink_ctx = AVFilterContext()
        avfilter_graph_create_filter(buffersink_ctx, buffersink_def, "out",
            null, null, filter_graph)
            .throwWhen(_ < 0, s"avfilter_graph_create_filter(out) failed", log, logCtx)

        // Set a binary option to an integer list
        // [If they are not set, all corresponding formats are accepted](https://github.com/bytedeco/javacpp-presets/blob/1.5.7/ffmpeg/src/gen/java/org/bytedeco/ffmpeg/global/avfilter.java#L691)
  /*
        val pix_fmts = Array(AV_PIX_FMT_GRAY8, AV_PIX_FMT_NONE)
        av_opt_set_int_list(buffersink_ctx, "pix_fmts", pix_fmts,
            AV_PIX_FMT_NONE, AV_OPT_SEARCH_CHILDREN)
            .throwWhen(_ < 0, s"av_opt_set_int_list failed", log, logCtx)
  */

        /*
         * Set the endpoints for the filter graph. The filter_graph will
         * be linked to the graph described by filters_descr.
         */

        /*
         * The buffer source output must be connected to the input pad of
         * the first filter described by filters_descr; since the first
         * filter input label is not specified, it is set to "in" by
         * default.
         */
        outputs.name(BytePointer("in"))
        outputs.filter_ctx(buffersrc_ctx)
        outputs.pad_idx(0)
        outputs.next(null)

        /*
         * The buffer sink input must be connected to the output pad of
         * the last filter described by filters_descr; since the last
         * filter output label is not specified, it is set to "out" by
         * default.
         */
        inputs.name(BytePointer("out"))
        inputs.filter_ctx(buffersink_ctx)
        inputs.pad_idx(0)
        inputs.next(null)

        avfilter_graph_parse_ptr(filter_graph, filterDescr, inputs, outputs, null)
            .throwWhen(_ < 0, s"avfilter_graph_parse_ptr failed", log, logCtx)

        avfilter_graph_config(filter_graph, null)
            .throwWhen(_ < 0, s"avfilter_graph_config failed", log, logCtx)

        succeeded = true
        Stream.fromAutoCloseable(Async[F].delay { FilterContext(filter_graph, buffersrc_ctx, buffersink_ctx, logCtx) })

      finally
        avfilter_inout_free(inputs)
        avfilter_inout_free(outputs)

    finally
      //avfilter_graph_free(filter_graph)
      //avfilter_free(buffersink_ctx)
      //avfilter_free(buffersrc_ctx)
      () // FIXME
