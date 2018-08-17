package sp.extractors

import java.util.UUID

import play.api.mvc.PathBindable.Parsing
import play.api.routing.sird.PathBindableExtractor

import scala.util.Try

/**
  * A segment is a string part separated by //
  * eg. segments in api/items/27 are "api", "items", and "27".
  */
object SegmentsExtractor {
  private def pathSegments(nondecoded: String): List[String] = nondecoded.split("/").filter(_.nonEmpty).toList

  implicit object bindableJavaUUID extends Parsing[List[String]](
    pathSegments,
    x => x.mkString("/"),
    (key: String, _: Exception) => s"$key can not be parsed into Segments."
  )

  val Segments = new PathBindableExtractor[List[String]]()
}