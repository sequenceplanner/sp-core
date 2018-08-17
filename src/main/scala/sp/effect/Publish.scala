package sp.effect

import play.api.libs.json.{JsValue, Json}
import sp.domain.{APISP, JSWrites, SPHeader}

object Publish {
  trait PublishRequest[F[_]] {
    def request(h: SPHeader, b: APISP): F[Unit]
  }

  trait PublishResponse[F[_]] {
    def respond[B: JSWrites](h: SPHeader, b: B): F[Unit]
  }

  object PublishRequest {
    def apply[F[_]](implicit F: PublishRequest[F]) = F
  }

  object PublishResponse {
    implicit def toJsValue[B: JSWrites](b: B): JsValue = Json.toJson(b)
    def apply[F[_]](implicit F: PublishResponse[F]) = F
    def respond[F[_], B: JSWrites](h: SPHeader, b: B)(implicit F: PublishResponse[F]) = F.respond(h, b)
    def respondN[F[_]](h: SPHeader, xs: JsValue*)(implicit F: PublishResponse[F]) = xs.foreach(F.respond(h, _))
  }
}
