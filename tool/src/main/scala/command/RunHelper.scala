package jp.ken1ma.kaska.multimedia
package tool
package command

import java.nio.file.Path

import org.bytedeco.ffmpeg.avformat.AVStream
import org.bytedeco.ffmpeg.avutil.AVFrame

object RunHelper:
  extension (stream: AVStream)
    def width: Int = stream.codecpar.width
    def height: Int = stream.codecpar.height

  extension (frm: AVFrame)
    def keyFrame: Boolean = frm.key_frame == 1

  extension (path: Path)
    def name: String = path.getFileName.toString

    def baseName: String =
      val name = path.name
      name.indexOf('.') match
        case -1 => name
        case firstDot => name.take(firstDot)
