package sp.websocket

import akka.NotUsed
import akka.actor.ActorRef
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.stream.FlowShape
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Sink, Source}
import play.api.mvc.WebSocket
import sp.MessageDomain
import sp.domain.Logic._
import sp.domain.{SPAttributes, SPMessage}

object WebSocketHandler {
  val holdFlow = Flow.fromGraph(new HoldWithInitial(MessageDomain()))

  /**
    * Holds the current domain that incoming API messages must adhere to.
    */
  private val messageDomain: Flow[SocketAPI, MessageDomain, NotUsed] = {
    Flow[SocketAPI]
      .scan(MessageDomain()) { (domain, value) =>
        value match {
          case FilterBody(body) => domain.mergeBody(body)

          case FilterHeader(header) => domain.mergeHeader(header)

          case ClearFilters(keysToKeep) =>
            if (keysToKeep.isEmpty) MessageDomain()
            else domain.filterByKeys(keysToKeep)
          case _ => domain
        }
      }
      .via(holdFlow)
  }

  private def parseToAPI(s: String): Option[SocketAPI] = SPAttributes.fromJson(s).flatMap(_.to[SocketAPI]).toOption

  def handleRequest(mediator: ActorRef, pubsub: Source[SPMessage, Unit]): WebSocket = {
    WebSocket.accept[String, String] { _ =>

      val publishMessageToBus = Flow[SocketAPI].collect { case PublishMessage(msg, topic) =>
          val res = Publish(topic, msg.toJson)
          println(s"toPublishMessage: $res")
          res
      }.to(Sink.actorRef(mediator, onCompleteMessage = "Killing me"))

      val apiMessageFlow: Flow[String, MessageDomain, NotUsed] = Flow[String]
        .map(parseToAPI)
        .flattenOption
        .via(subSink(publishMessageToBus))
        .via(messageDomain)

      val filteredMessages = apiMessageFlow
        .zipWith(pubsub)((domain, msg) => Some(msg).filter(domain.isAllowedMessage))
        .flattenOption

      /*
      val toStrict = filteredMessages.map(msg => TextMessage(msg.toJson))
      val inject = toStrict.keepAlive(2 seconds, () => TextMessage("keep-alive"))
      */

      val show: Flow[String, String, NotUsed] = filteredMessages.map(_.toJson)


      show
      //Flow[String].map(_ => "Got it.")
    }
  }

  /**
    * Runs the flow through an additional sink. Does not affect the flow in any way, i.e. no messages
    * are consumed by the additional sink.
    */
  private def subSink[V, Mat](sink: Sink[V, Mat]): Flow[V, V, NotUsed] = Flow.fromGraph(GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val split = builder.add(Broadcast[V](2))
    split ~> builder.add(sink)

    FlowShape(split.in, split.out(1))
  })

  implicit class FlowSyntax[In, Out, Mat](flow: Flow[In, Option[Out], Mat]) {
    def flattenOption: Flow[In, Out, Mat] = flow.collect { case Some(v) => v }
  }
}