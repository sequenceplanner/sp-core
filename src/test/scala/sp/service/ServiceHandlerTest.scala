package sp.service

import akka.actor.{Actor, ActorRef, ActorSystem, Terminated}
import akka.testkit.{TestKit, TestProbe}
import org.scalatest._
import sp.domain.APISP.StatusResponse
import sp.domain.{APISP, JSWrites, SPHeader, SPMessage}
import sp.effect.Publish.{PublishRequest, PublishResponse}
import sp.service.APIServiceHandler._
import sp.service.ServiceHandler.{ResponseData, WatchService}

import scala.concurrent.duration._
import akka.util.Timeout


class ServiceHandlerTest extends TestKit(ActorSystem("ServiceHandlerTest")) with FreeSpecLike with BeforeAndAfterAll {
  implicit val timeout = Timeout(5 seconds)

  import ServiceHandler.State

  type SH = ServiceHandler
  trait ServiceFfects extends PublishResponse[SH] with PublishRequest[SH] with WatchService[SH]

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  class Messenger(handler: ActorRef, probe: TestProbe) {

    def expectGetServices[B](expectedBody: B): (SPHeader, B) = {
      val expectedHeader = header.copy(from = APIServiceHandler.service, to = header.from)
      val msg = SPMessage.make(header.copy(to = APIServiceHandler.service), GetServices)
      handler ! msg.toJson
      probe.expectMsg((expectedHeader, expectedBody))
    }

    def expectStatusResponse(message: StatusResponse, sender: ActorRef) = {
      val responseMsg = SPMessage.make(header.copy(to = APIServiceHandler.service), message)
      handler.tell(responseMsg.toJson, sender)
      probe.expectMsg((header.copy(from = APIServiceHandler.service, to = header.from), ServiceAdded(message)))
    }

    def expectMaybeStatusResponse(message: StatusResponse, sender: ActorRef)(expectedResponse: Option[APIServiceHandler.Response] = None): Unit = {
      val responseMsg = SPMessage.make(header.copy(to = APIServiceHandler.service), message)
      handler.tell(responseMsg.toJson, sender)
      if (expectedResponse.nonEmpty){
        expectedResponse.map { expected =>
          probe.expectMsg((header.copy(from = APIServiceHandler.service, to = header.from), expected))
        }
      }
    }

    def removeResponse(message: StatusResponse)(expectedResponse: APIServiceHandler.Response) = {
      val responseMsg = SPMessage.make(header.copy(to = APIServiceHandler.service), RemoveService(message))
      handler ! responseMsg.toJson
      probe.expectMsg((header.copy(from = APIServiceHandler.service, to = header.from), expectedResponse))
    }

    def testTickRequest(): Unit = {
      handler ! ServiceHandler.Tick
      probe.expectMsgPF(2 seconds, "Tick request") {
        case (header: SPHeader, APISP.StatusRequest) if header.from == APIServiceHandler.service => header
      }
    }

    /**
      * Sloppy implementation because I could not find a way to expect several messages in any order using a PF.
      */
    def terminate[B](sender: ActorRef)(expect: B*): Unit = {
      var notYetSeen = expect.toSet
      handler ! ServiceHandler.TerminatedSender(sender)
      (0 until expect.size).foreach { _ =>
        probe.expectMsgPF(5 seconds, "Service removal confirmation") {
          case (h: SPHeader, r: B) if h.from == APIServiceHandler.service && notYetSeen.contains(r) =>
            notYetSeen = notYetSeen - r
      }}
    }
  }

  val serviceName = "test-service"
  val header = SPHeader(from = "ServiceHandlerTest")

  val state = State()

  "State" - {
    "addResponse works" in {
      val sender = TestProbe()
      val r1 = ResponseData(StatusResponse(serviceName), sender.ref)

      val s2 = state.copy(waiting = Set(r1))

      assert(s2.waiting.contains(r1), "Equality checks are unsound")

      assert(!s2.hasResponse(r1), "hasResponse should only return true if the response is in 'responses', not in 'waiting'")


      val s3 = s2.addResponse(r1)
      assert(s3.hasResponse(r1))

      // Adding a response should remove it from "waiting"
      assert(!s3.waiting.contains(r1))
    }

    "removeAPIResponse removes responses" in {
      val sender = TestProbe()
      val statusResponse = StatusResponse(serviceName + "2")
      val r1 = ResponseData(StatusResponse(serviceName), sender.ref)
      val r2 = ResponseData(statusResponse, sender.ref)

      val s2 = state.copy(responses = Set(r1), waiting = Set(r1, r2))
      assert(!s2.removeAPIResponse(statusResponse).waiting.contains(r2), "removal using StatusResponse should remove the response from waiting")
      assert(!s2.hasResponse(r2), "removal using StatusResponse should remove the response from responses")
    }

    "finds responses from particular sender" in {
      val sender1 = TestProbe()
      val sender2 = TestProbe()
      val r1 = ResponseData(StatusResponse(serviceName), sender1.ref)
      val r2 = ResponseData(StatusResponse(serviceName + "2"), sender1.ref)
      val r3 = ResponseData(StatusResponse(serviceName), sender2.ref)

      val s2 = state.copy(responses = Set(r1, r2, r3))
      assert(s2.responsesFromSender(sender1.ref) == Set(r1, r2), "Did not find correct responses given a particular sender")

    }

    "minus (-) removes from both responses and waiting" in {
      val sender = TestProbe()
      val r1 = ResponseData(StatusResponse(serviceName), sender.ref)

      val s1 = state.copy(responses = Set(r1), waiting = Set(r1))
      assert((s1 - r1) == State())
    }
  }

  "ResponseId" - {
    "Correctly generates an ID for a response" in {
      val response = StatusResponse(serviceName)
      val response2 = StatusResponse(serviceName, instanceName = "theInstance")

      assert(ServiceHandler.statusResponseId(response) == response.service)
      assert(ServiceHandler.statusResponseId(response2) == s"${response.service}-${response2.instanceName}")
    }
  }

  "ParseServiceMessage" - {
    "ParseServiceMessage methods are correct" in {
      val parse = new ServiceHandler.ParseServiceMessage {}
      val goodStatusResponse = StatusResponse(serviceName)
      val msg = SPMessage.make(SPHeader(), goodStatusResponse)

      assert(parse.parseApiMessage(msg).nonEmpty, "parseApiMessage should correctly parse a valid SPMessage")

      val goodRequest = SPMessage.make(SPHeader(to = APIServiceHandler.service), GetServices)
      val badRequest = SPMessage.make(SPHeader(), goodRequest)

      assert(parse.parseRequest(goodRequest).nonEmpty, "parseRequest should correctly parse a valid SPMessage")
      assert(parse.parseRequest(badRequest).isEmpty, "parseRequest should only parse messages that is sent to it. (The SPHeader needs to=\"ServiceHandler\")")
    }
  }

  "ServiceHandler Actor" - {
    def mockEffect(probe: TestProbe) = new ServiceFfects {
      override def respond[B: JSWrites](h: SPHeader, b: B)(implicit handler: SH): Unit = probe.ref ! (h, b)
      override def request(h: SPHeader, b: APISP)(implicit handler: SH): Unit = probe.ref ! (h, b)

      // TODO: How should we test this?
      override def watch(service: ActorRef)(implicit handler: SH) = service
    }


    "Correctly adds and reports responses" in {
      val effectProbe = TestProbe("effectProbe")
      val serviceActor = TestProbe("serviceActor")


      val serviceHandler = system.actorOf(ServiceHandler.props(mockEffect(effectProbe)))
      val messenger = new Messenger(serviceHandler, effectProbe)

      val response = StatusResponse(serviceName)

      messenger.expectStatusResponse(response, serviceActor.ref)
      messenger.expectMaybeStatusResponse(response, serviceActor.ref)() // Checks that response is not added twice even if two messages are sent
      messenger.expectGetServices(expectedBody = Services(List(response)))

      messenger.removeResponse(response)(expectedResponse = ServiceRemoved(response))

      messenger.expectGetServices(expectedBody = Services(List()))
    }


    "Tick calls request" in {
      val probe = TestProbe()

      val serviceHandler = system.actorOf(ServiceHandler.props(mockEffect(probe)))
      val messenger = new Messenger(serviceHandler, probe)

      messenger.testTickRequest()
    }


    "Handles termination properly" in {
      val effectProbe = TestProbe()
      val serviceActorA = TestProbe("serviceActorA")
      val serviceActorB = TestProbe("serviceActorB")

      val serviceHandler = system.actorOf(ServiceHandler.props(mockEffect(effectProbe)))
      val messenger = new Messenger(serviceHandler, effectProbe)

      val responsesA = List.fill(5)(StatusResponse(serviceName)).zipWithIndex
        .map { case (res, i) => res.copy(service = s"${res.service}-A-$i" ) }

      val responsesB = List.fill(5)(StatusResponse(serviceName)).zipWithIndex
        .map { case (res, i) => res.copy(service = s"${res.service}-B-$i" ) }


      responsesA.foreach(r => messenger.expectStatusResponse(r, serviceActorA.ref))
      responsesB.foreach(r => messenger.expectStatusResponse(r, serviceActorB.ref))

      val expected = responsesA.map(ServiceRemoved)
      messenger.terminate(serviceActorA.ref)(expected:_*)
    }

  }
}