package sp.effect

import play.api.libs.json.{JsValue, Json}
import sp.domain.{APISP, JSWrites, SPHeader}

object Publish {
  trait PublishRequest[C] {
    def request(h: SPHeader, b: APISP)(implicit context: C): Unit
  }

  trait PublishResponse[C] {
    def respond[B: JSWrites](h: SPHeader, b: B)(implicit context: C): Unit
    def respondN(h: SPHeader, xs: JsValue*)(implicit context: C) = xs.foreach(respond(h, _))
  }

  object PublishResponse {
    implicit def toJsValue[B: JSWrites](b: B): JsValue = Json.toJson(b)
    def respond[B: JSWrites, C](h: SPHeader, b: B)(implicit context: C, F: PublishResponse[C]) = F.respond(h, b)
    def respondN[C](h: SPHeader, xs: JsValue*)(implicit context: C, F: PublishResponse[C]) = xs.foreach(F.respond(h, _))
  }
}
