package sp.service

import akka.actor._
import sp.domain._


trait ServiceSupport extends ServiceCommunicationSupport with MessageBussSupport

trait ServiceCommunicationSupport {
  val context: ActorContext
  private var shComm: Option[ActorRef] = None
  def triggerServiceRequestComm(resp: APISP.StatusResponse): Unit = {
    if (shComm.isEmpty){
      val x = context.actorOf(Props(classOf[ServiceHandlerComm], resp))
      shComm = Some(x)
    }
  }
  def updateServiceRequest(resp: APISP.StatusResponse): Unit = {
    shComm.foreach(_ ! resp)
  }



}

class ServiceHandlerComm(resp: APISP.StatusResponse) extends Actor with MessageBussSupport {
  var serviceResponse: APISP.StatusResponse = resp
  subscribe(APISP.serviceStatusRequest)
  sendEvent(SPHeader(from = serviceResponse.instanceName, to = APIServiceHandler.service))

  override def receive: Receive = {
    case x: APISP.StatusResponse if sender() != self => serviceResponse = x
    case x: String if sender() != self =>
      for {
        mess <- SPMessage.fromJson(x)
        h <- mess.getHeaderAs[SPHeader]
        b <- mess.getBodyAs[APISP] if b == APISP.StatusRequest
      } yield {
        sendEvent(h.copy(to = h.from, from = serviceResponse.instanceID.toString))
      }
  }

  override def postStop() = {
    println("ServiceCommSupportActor closed for: " + serviceResponse.instanceID)
    publish(APISP.serviceStatusResponse,
      SPMessage.makeJson(
        SPHeader(to = APIServiceHandler.service, from = serviceResponse.instanceID.toString ),
        APIServiceHandler.RemoveService(serviceResponse)))
    super.postStop()
  }

  def sendEvent(h: SPHeader) =
    publish(APISP.serviceStatusResponse, SPMessage.makeJson(h, serviceResponse))
}



case object Tick
class AskActor(mess: SPMessage, requestTopic: String, replyTopic: String) extends Actor with MessageBussSupport {
  subscribe(replyTopic)
  publish(requestTopic, mess.toJson)

  val senderHeader = mess.getHeaderAs[SPHeader]
  if (senderHeader.isEmpty) {
    context.parent ! APISP.SPError("Need an SPHeader")
    self ! PoisonPill
  }

  // TODO: Handle answers from multiple services later
  var servicesThatAnswered: Map[String, List[SPMessage]] = Map()

  import scala.concurrent.duration._
  import context.dispatcher
  val ticker = context.system.scheduler.scheduleOnce(2 seconds, self, Tick)

  override def receive = {
    case x: String =>
      for {
        mess <- SPMessage.fromJson(x)
        sendH <- senderHeader
        h <- mess.getHeaderAs[SPHeader] if h.reqID == sendH.reqID
      } yield {
        val apisp = mess.getBodyAs[APISP]
        apisp.foreach{
          case x: APISP.SPACK =>
            ticker.cancel()
          case x =>
            ticker.cancel()
            context.parent ! x
            self ! PoisonPill
        }
        if (apisp.isEmpty) {
          context.parent ! mess
        }

      }
    case Tick =>
      context.parent ! APISP.SPError("No ACK or answer after 2 seconds")
      self ! PoisonPill
  }
}
