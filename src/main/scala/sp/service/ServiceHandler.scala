package sp.service


import akka.actor._
import sp.domain.APISP.StatusResponse
import sp.domain._
import sp.Util.SPMessageSyntax._
import sp.effect.Kinds.Id
import sp.effect.Publish._
import sp.service.ServiceHandler.WatchService


/**
  * Created by kristofer on 2017-02-27.
  * Edited by Jonathan Krän on 2018-08-17
  *
  * Monitors services and keeps track if they are removed or not behaving.
  *
  */
class ServiceHandler extends Actor with MessageBussSupport {

  import scala.concurrent.duration._
  import context.dispatcher
  val ticker = context.system.scheduler.schedule(5 seconds, 5 seconds, self, Tick)

  subscribe(APIServiceHandler.topicRequest)
  subscribe(APISP.serviceStatusResponse)

  def onReceiveMessage(state: State): PartialFunction[Any, State] = {
    case x: String if sender() != self =>
      val spMessage = SPMessage.fromJson(x)
      val s1 = spMessage
        .getAs[SPHeader, APIServiceHandler.Request]
        .ifHeader(_.to == APIServiceHandler.service)
        .map((header, body) => handleServiceRequest(state, header, body))

      spMessage
        .getAs[SPHeader, APISP]
        .flatMapBody { case r: StatusResponse => Some(r) }
        .map((header, body) => handleStatusResponse(s1.getOrElse(state), header, body, sender()))
        .getOrElse(state)

    case Terminated(ref) => handleTermination(state, ref)

    case Tick => updateResponseState(state)
  }

  private def updateResponseState[F[_]: PublishRequest](state: State): State = {
    PublishRequest[F].request(SPHeader("ServiceHandler"), APISP.StatusRequest)

    state.copy(responses = Set(), waiting = state.responses)
  }

  private def handleTermination[F[_]: PublishResponse](state: State, sender: ActorRef): State = {
    val res = state.responsesFromSender(sender)
    val header = SPHeader(from = APIServiceHandler.service)
    res.map(_.inner).foreach { response =>
      PublishResponse[F].respond(header, APIServiceHandler.ServiceRemoved(response))
    }

    state -- res
  }

  private def handleServiceRequest[F[_]: PublishResponse](state: State, header: SPHeader, body: APIServiceHandler.Request): State = {
    import APIServiceHandler.{GetServices, RemoveService, service, Services}
    body match {
      case GetServices =>
        val responses = state.responses.map(_.inner).toList
        val newHeader = header.copy(from = service, to = header.from)

        PublishResponse[F].respond(newHeader, Services(responses))

        state

      case RemoveService(response) =>
        PublishResponse[F].respond(SPHeader(from = APIServiceHandler.service), APIServiceHandler.ServiceRemoved(response))
        state.removeAPIResponse(response)
    }
  }

  private def handleStatusResponse[F[_]: PublishResponse : WatchService](state: State, header: SPHeader, response: StatusResponse, sender: ActorRef): State = {
    WatchService[F].watch(sender)
    val responseData = ResponseData(response, sender)
    val isNewResponse = !state.hasResponse(responseData)

    if (isNewResponse) {
      val header = SPHeader(from = APIServiceHandler.service)
      val body = APIServiceHandler.ServiceAdded(response)

      PublishResponse[F].respond(header, body)
    }

    state.addResponse(responseData)
  }

  private def stateReceive(state: State): Receive = onReceiveMessage(state) andThen (s => context.become(stateReceive(s)))
  override def receive = stateReceive(State())


  case class ResponseData(inner: StatusResponse, sender: ActorRef) {
    def service: String = inner.service
    def instanceID: Option[ID] = inner.instanceID
    def instanceName: String = inner.instanceName
    def id: String = statusResponseId(inner)
  }

  def statusResponseId(response: StatusResponse): String = {
    def prefix(s: String): String = s + (if (s.nonEmpty) "-" else "")
    val instance = response.instanceID.map(_.toString).getOrElse("")

    response.service + prefix(response.instanceName) + prefix(instance)
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

  implicit val idCommunication: PublishResponse[Id] = (h: SPHeader, b: APIServiceHandler.Response) => {
    publish(APISP.serviceStatusRequest, SPMessage.makeJson(h, b))
  }

  implicit val idRequest: PublishRequest[Id] = (h: SPHeader, b: APISP) => {
    publish(APIServiceHandler.topicResponse, SPMessage.makeJson(h, b))
  }

  implicit val idWatch: WatchService[Id] = (service: ActorRef) => context.watch(service)
}

object ServiceHandler {
  case object Tick

  trait WatchService[F[_]] {
    def watch(service: ActorRef): F[ActorRef]
  }

  object WatchService {
    def apply[F[_]](implicit F: WatchService[F]) = F
  }

  def props = Props(new ServiceHandler())
}
