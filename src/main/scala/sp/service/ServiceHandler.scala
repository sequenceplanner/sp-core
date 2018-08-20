package sp.service


import akka.actor._
import sp.domain.APISP.StatusResponse
import sp.domain._
import sp.Util.SPMessageSyntax._
import sp.domain
import sp.effect.BusSupport
import sp.effect.Publish._
import sp.service.ServiceHandler.ParseServiceMessage


/**
  * Created by kristofer on 2017-02-27.
  * Edited by Jonathan Krän on 2018-08-17
  *
  * Monitors services and keeps track if they are removed or not behaving.
  *
  */
class ServiceHandler()(implicit val effects: ServiceHandler.Effects) extends Actor with MessageBussSupport with ParseServiceMessage {
  import sp.service.ServiceHandler.{State, ResponseData}
  import scala.concurrent.duration._

  val ticker = context.system.scheduler.schedule(5 seconds, 5 seconds, self, Tick)(context.dispatcher)

  implicit val selfImplicit = this

  def combineMessageHandlers(handlers: ((SPMessage, State) => Option[State])*)(msg: SPMessage, state: State): State = {
    handlers
      .map(h => h(msg, state))
      .collectFirst { case Some(s) => s }
      .getOrElse(state)
  }

  def onReceiveMessage(state: State): PartialFunction[Any, State] = {
    case x: String if sender() != self =>
      val message = SPMessage.fromJson(x)
      def onRequest(m: SPMessage, s: State) = parseRequest(m).map { case (header, body) =>
        handleServiceRequest(s, header, body)
      }

      def onStatusResponse(m: SPMessage, s: State) = parseApiMessage(m).map { case (header, body) =>
        handleStatusResponse(s, header, body, sender())
      }

      val newState = message.map(msg => combineMessageHandlers(onRequest, onStatusResponse)(msg, state))
      newState.getOrElse(state)

    case Terminated(ref) => handleTermination(state, ref)

    case Tick => updateResponseState(state)
  }

  private def updateResponseState(state: State): State = {
    effects.request(SPHeader("ServiceHandler"), APISP.StatusRequest)

    state.copy(responses = Set(), waiting = state.responses)
  }

  private def handleTermination(state: State, sender: ActorRef): State = {
    val res = state.responsesFromSender(sender)
    val header = SPHeader(from = APIServiceHandler.service)
    res.map(_.inner).foreach { response =>
      effects.respond(header, APIServiceHandler.ServiceRemoved(response))
    }

    state -- res
  }

  private def handleServiceRequest(state: State, header: SPHeader, body: APIServiceHandler.Request): State = {
    import APIServiceHandler.{GetServices, RemoveService, service, Services}
    body match {
      case GetServices =>
        val responses = state.responses.map(_.inner).toList
        val newHeader = header.copy(from = service, to = header.from)

        effects.respond(newHeader, Services(responses))

        state

      case RemoveService(response) =>
        effects.respond(SPHeader(from = APIServiceHandler.service), APIServiceHandler.ServiceRemoved(response))
        state.removeAPIResponse(response)
    }
  }

  private def handleStatusResponse(state: State, header: SPHeader, response: StatusResponse, sender: ActorRef): State = {
    effects.watch(sender)
    val responseData = ResponseData(response, sender)
    val isNewResponse = !state.hasResponse(responseData)

    if (isNewResponse) {
      val header = SPHeader(from = APIServiceHandler.service)
      val body = APIServiceHandler.ServiceAdded(response)

      effects.respond(header, body)
    }

    state.addResponse(responseData)
  }

  private def stateReceive(state: State): Receive = onReceiveMessage(state) andThen (s => context.become(stateReceive(s)))
  override def receive = stateReceive(State())
}

object ServiceHandler {
  case object Tick

  trait WatchService[C] {
    def watch(service: ActorRef)(implicit context: C): ActorRef
  }

  object WatchService {
    def apply[F[_], C](implicit context: C, F: WatchService[C]) = F
  }

  case class State(responses: Set[ResponseData] = Set(), waiting: Set[ResponseData] = Set()) {
    def hasResponse(response: ResponseData): Boolean = responses.contains(response)
    def addResponse(response: ResponseData) = {
      copy(responses = responses + response, waiting = waiting.filterNot(_.id == response.id))
    }
    def removeAPIResponse(response: StatusResponse): State = {
      val id = statusResponseId(response)
      copy(responses = responses.filterNot(_.id == id), waiting = waiting.filterNot(_.id == id))
    }

    def responsesFromSender(sender: ActorRef): Set[ResponseData] = responses.filter(_.sender == sender)

    def -(response: ResponseData): State = copy(responses = responses - response, waiting = waiting - response)
    def --(rs: Set[ResponseData]): State = copy(responses = responses -- rs, waiting = waiting -- rs)
  }

  case class ResponseData(inner: StatusResponse, sender: ActorRef) {
    def service: String = inner.service
    def instanceID: Option[ID] = inner.instanceID
    def instanceName: String = inner.instanceName
    def id: String = statusResponseId(inner)
  }

  def statusResponseId(response: StatusResponse): String = {
    def prefix(s: String): String = (if (s.nonEmpty) "-" else "") + s
    val instance = response.instanceID.map(_.toString).getOrElse("")

    response.service + prefix(response.instanceName) + prefix(instance)
  }

  val defaultEffect = new PublishResponse[ServiceHandler]
    with PublishRequest[ServiceHandler]
    with WatchService[ServiceHandler]
    with BusSupport {

    override def respond[B: JSWrites](h: SPHeader, b: B)(implicit handler: ServiceHandler): Unit = {
      if (!busCreated) initBus(handler)

      publishOnBus(APISP.serviceStatusRequest, SPMessage.makeJson(h, b))
    }

    override def request(h: SPHeader, b: APISP)(implicit handler: ServiceHandler): Unit = {
      if (!busCreated) initBus(handler)

      publishOnBus(APIServiceHandler.topicResponse, SPMessage.makeJson(h, b))
    }

    override def watch(service: ActorRef)(implicit handler: ServiceHandler) = handler.context.watch(service)

    override def busSubscriptionTopics = Seq(APIServiceHandler.topicRequest, domain.APISP.serviceStatusResponse)
  }

  type Effects = PublishResponse[ServiceHandler] with PublishRequest[ServiceHandler] with WatchService[ServiceHandler]

  def props[F[_]](effect: Effects = defaultEffect) = {
    implicit val eff = effect
    Props(new ServiceHandler())
  }

  trait ParseServiceMessage {
    def parseRequest(message: SPMessage): Option[(SPHeader, APIServiceHandler.Request)] = {
      message.getAs[SPHeader, APIServiceHandler.Request].ifHeader(_.to == APIServiceHandler.service)
    }

    def parseApiMessage(message: SPMessage): Option[(SPHeader, StatusResponse)] = {
      message.getAs[SPHeader, StatusResponse].flatMapBody { case r: StatusResponse => Some(r) }
    }
  }
}
