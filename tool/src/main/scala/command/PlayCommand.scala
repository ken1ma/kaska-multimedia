package jp.ken1ma.kaska.multimedia
package tool
package command

import java.nio.file.Path

import cats.effect.Async
import cps.async
import cps.monads.catsEffect.given
import org.log4s.getLogger

import org.bytedeco.javacpp._
import org.bytedeco.skia._
import org.bytedeco.skia.global.Skia._

class PlayCommand[F[_]: Async]:
  def run(in: String): F[Unit] = async[F] {
    val inFile = Path.of(in) // TODO supports https
    println(s"inFile = $inFile")

    // based on https://github.com/bytedeco/javacpp-presets/tree/master/skia
    /*
      macosx-arm64.jar is missing in https://repo1.maven.org/maven2/org/bytedeco/skia/2.80.3-1.5.7/
        [error] java.lang.UnsatisfiedLinkError: no jniSkia in java.library.path: /Users/kenichi/Library/Java/Extensions:/Library/Java/Extensions:/Network/Library/Java/Extensions:/System/Library/Java/Extensions:/usr/lib/java:.
        [error] 	at java.base/java.lang.ClassLoader.loadLibrary(ClassLoader.java:2429)
        [error] 	at java.base/java.lang.Runtime.loadLibrary0(Runtime.java:818)
        [error] 	at java.base/java.lang.System.loadLibrary(System.java:1989)
        [error] 	at org.bytedeco.javacpp.Loader.loadLibrary(Loader.java:1800)
        [error] 	at org.bytedeco.javacpp.Loader.load(Loader.java:1402)
        [error] 	at org.bytedeco.javacpp.Loader.load(Loader.java:1214)
        [error] 	at org.bytedeco.javacpp.Loader.load(Loader.java:1190)
        [error] 	at org.bytedeco.skia.global.Skia.<clinit>(Skia.java:14)
        [error] 	at java.base/java.lang.Class.forName0(Native Method)
        [error] 	at java.base/java.lang.Class.forName(Class.java:467)
        [error] 	at org.bytedeco.javacpp.Loader.load(Loader.java:1269)
        [error] 	at org.bytedeco.javacpp.Loader.load(Loader.java:1214)
        [error] 	at org.bytedeco.javacpp.Loader.load(Loader.java:1190)
        [error] 	at org.bytedeco.skia.sk_imageinfo_t.<clinit>(sk_imageinfo_t.java:16)
    */
    val info = sk_imageinfo_t() // sk_imageinfo_t
    info.width(320)
    info.height(240)
    info.colorType(BGRA_8888_SK_COLORTYPE)
    info.alphaType(PREMUL_SK_ALPHATYPE)

    val surface = sk_surface_new_raster(info, 0, null) // sk_surface_t
    val canvas = sk_surface_get_canvas(surface) // sk_canvas_t
  }
