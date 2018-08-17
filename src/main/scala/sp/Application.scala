package sp

import java.util.UUID

import akka.actor.ActorRef
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}
import akka.pattern.ask
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import akka.util.Timeout
import play.api.ApplicationLoader.Context
import play.api.i18n._
import play.api.mvc._
import play.api.routing._
import play.api.routing.sird._
import play.api.{routing, _}
import play.filters.HttpFiltersComponents
import sp.domain.{APISP, SPAttributes, SPMessage, SPValue}
import sp.service.ServiceHandler
import internal.Encoding._
import internal.Resource._
import sp.extractors._
import sp.websocket.WebSocketHandler

import scala.concurrent.Future
import scala.concurrent.duration._


class CoreApplicationLoader extends ApplicationLoader {
  override def load(context: ApplicationLoader.Context): Application = new CoreComponents(context).application
}

class CoreComponents(context: Context) extends BuiltInComponentsFromContext(context)
with I18nComponents with HttpFiltersComponents {
  import Response._
  implicit val timeout = Timeout(5 seconds)
  implicit val system = actorSystem
  implicit val mimeTypes = fileMimeTypes
  implicit val implicitContext = context

  val cluster = new AkkaCluster(system)
  system.actorOf(ServiceHandler.props)

  val log = org.slf4j.LoggerFactory.getLogger(getClass.getName)

  val mediator = DistributedPubSub(system).mediator

  val webFolder: String = system.settings.config getString "sp.webFolder"
  val devFolder: String = system.settings.config getString "sp.devFolder"
  val buildFolder: String = system.settings.config getString "sp.buildFolder"
  val devMode: Boolean = system.settings.config getBoolean "sp.devMode"
  val interface = system.settings.config getString "sp.interface"
  val port = system.settings.config getInt "sp.port"
  val srcFolder: String = if(devMode)
    devFolder else buildFolder

  LoggerConfigurator(context.environment.classLoader).foreach {
    _.configure(context.environment, context.initialConfiguration, Map.empty)
  }


  private def subscribeClient(clientId: UUID, topic: String)(ref: ActorRef): Unit = {
    mediator ! Subscribe(topic, ref)
    mediator ! Subscribe(clientId.toString, ref)
  }

  private def mockSubscribe(clientId: UUID, topic: String)(ref: ActorRef): Unit = {
    val msg = "{\"header\":{\"header\":\"headerValue\"},\"body\":{\"body\":\"bodyValue\"}}"
    system.scheduler.schedule(0 milliseconds, 1 second, ref, msg)
  }

  private def subscribeToBus(withRef: ActorRef => Unit): Source[SPMessage, Unit] = {
    Source.actorRef[Any](1000, OverflowStrategy.dropNew).mapMaterializedValue(withRef)
      .collect { case s: String => SPMessage.fromJson(s) }
      .collect { case Some(v) => v }
  }

  override val router: Router = combineRouters(mainRouter, apiRouter)

  def apiRouter: Router = SimpleRouter({
    case GET(p"/") => Action { _ =>
      sendFile(srcFolder + "/index.html")
        .orElse(sendResource("index.html"))
        .orElse(sendResource(srcFolder + "/index.html"))
        .orElse(sendResource(webFolder + "/index.html"))
        .getOrElse(Results.NotFound("Could not find the requested resource."))
    }

    case GET(p"/ask") => BadRequest(Text.AskRequiresTopic)

    case POST(p"/ask/$topic/$service*") => handleAskTopic(topic, service)
    case POST(p"/ask/$topic") => handleAskTopic(topic, "")

    case POST(p"/publish/$topic/$service*") => handlePublishTopic(topic, service)
    case POST(p"/publish/$topic") => handlePublishTopic(topic, "")

    case _ => NotFound(Text.NotFound)
  }).withPrefix("/api")



  def mainRouter: Router = SimpleRouter({
    case GET(p"/socket/$topic/$id") =>
      val websocket = for (id <- UUIDExtractor.fromString(id)) yield {
        val pubsub = subscribeToBus(mockSubscribe(id, topic))
        WebSocketHandler.handleRequest(mediator, pubsub)
      }

      websocket.getOrElse(BadRequest(Text.BadUUID(id)))

    //case _ => NotFound(Text.NotFound)
  })


  def handleAskTopic(topic: String, service: String): Action[AnyContent] = Action.async { req =>
    val msg = messageFromAnyContent(req.body)
      .map(m => if (service.nonEmpty) appendToHeader(m, ("service", SPValue(service))) else m)
      .getOrElse(emptySPMessage)

    mediator.ask(Publish(topic, msg.toJson))
      .mapTo[String]
      .map(Results.Ok(_))
      .recoverWith { case _ =>
        val res = SPMessage.make(msg.header, APISP.SPError("No service answered the request"))
        Future.successful(Results.Ok(res.toJson))
    }
  }

  def handlePublishTopic(topic: String, service: String) = Action { req =>
    val msg = messageFromAnyContent(req.body)
      .map(applyIf(service.nonEmpty)(appendToHeader(_, ("service", SPValue(service)))))
      .getOrElse(emptySPMessage)

      mediator ! Publish(topic, msg.toJson)
      val results = SPMessage.make(msg.header, APISP.SPACK()) // SPAttributes("result" ->"Message sent")
      Results.Ok(results.toJson)
  }

  def messageFromAnyContent(content: AnyContent): Option[SPMessage] = content match {
    case AnyContentAsJson(json) => json.asOpt[SPMessage]
    case AnyContentAsText(s) => SPMessage.fromJson(s)
    case _ => None
  }


  def applyIf[A](p: Boolean)(f: A => A)(a: A): A = if (p) f(a) else a

  def appendToHeader(msg: SPMessage, pair: (String, SPValue)): SPMessage = msg.copy(header = msg.header + pair)
  private def emptySPMessage = SPMessage.make(SPAttributes.empty, SPAttributes.empty)

  private def combineRouters(router: Router, routers: Router*) = routing.SimpleRouter((router +: routers).map(_.routes).reduce(_ orElse _))

  object Response {
    def apply(status: Result) = Action(status)

    def Ok(msg: String) = Response(Results.Ok(msg))
    def BadRequest(msg: String) = Response(Results.BadRequest(msg))
    def NotFound(msg: String) = Response(Results.NotFound(msg))
  }

}