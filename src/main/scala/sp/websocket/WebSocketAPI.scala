package sp.websocket

import sp.domain.Logic.deriveFormatISA
import sp.domain.{SPMessage, SPValue}

sealed trait SocketAPI

case class PublishMessage(mess: SPMessage, topic: String = "services") extends SocketAPI
case class FilterHeader(keyValues: Map[String, Set[SPValue]]) extends SocketAPI
case class FilterBody(keyValues: Map[String, Set[SPValue]]) extends SocketAPI
case class ClearFilters(keys: Set[String] = Set()) extends SocketAPI

object SocketAPI {
  implicit val apiFormat = deriveFormatISA[SocketAPI]
}