package sp.extractors

import java.util.UUID

import play.api.mvc.PathBindable.Parsing
import play.api.routing.sird.PathBindableExtractor

import scala.util.Try

object UUIDExtractor {
  val uuidRegex = "([a-fA-F0-9]{8}-(?:[a-fA-F0-9]{4}-){3}[a-fA-F0-9]{12}){1}"
  def fromString(s: String): Option[UUID] = Try(UUID.fromString(s)).toOption

  case class JavaUUID(id: String) {
    require(id.matches(uuidRegex))
  }

  implicit object bindableJavaUUID extends Parsing[JavaUUID](
    JavaUUID.apply,
    _.id,
    (key: String, e: Exception) => s"$key can not be parsed into a java.util.UUID."
  )

  val javaUUID = new PathBindableExtractor[JavaUUID]()
}