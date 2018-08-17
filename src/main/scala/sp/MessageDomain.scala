package sp

import play.api.libs.json.{JsObject, JsValue}
import sp.domain.SPMessage
import sp.domain.logic.AttributeLogic._

/**
  * Domain that messages sent through the websocket must adhere to.
  */
case class MessageDomain(headerDomain: Map[String, Set[JsValue]] = Map(), bodyDomain: Map[String, Set[JsValue]] = Map()) {
  private def isAllowed(o: JsObject, domain: Map[String, Set[JsValue]]): Boolean = {
    domain.forall { case (k, vs) => o.get(k).forall(vs.contains) }
  }
  private def isAllowedHeader(header: JsObject): Boolean = isAllowed(header, headerDomain)
  private def isAllowedBody(body: JsObject): Boolean = isAllowed(body, bodyDomain)
  def isAllowedMessage(msg: SPMessage): Boolean = {
    isEmpty || (isAllowedHeader(msg.header) && isAllowedBody(msg.body))
  }

  def mergeHeader(other: Map[String, Set[JsValue]]): MessageDomain = copy(headerDomain = headerDomain ++ other)
  def mergeBody(other: Map[String, Set[JsValue]]): MessageDomain = copy(bodyDomain = bodyDomain ++ other)

  def filterByKeys(keys: Set[String]): MessageDomain = copy(
    headerDomain = headerDomain.filterKeys(keys.contains),
    bodyDomain = bodyDomain.filterKeys(keys.contains),
  )

  def isEmpty: Boolean = headerDomain.isEmpty && bodyDomain.isEmpty
}