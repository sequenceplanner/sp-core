package sp.service

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import org.scalatest._
import sp.domain.APISP.StatusResponse
import sp.domain.{SPHeader, SPMessage}
import sp.service.APIServiceHandler.GetServices
import sp.service.ServiceHandler.ResponseData

class ServiceHandlerTest extends TestKit(ActorSystem("ServiceHandlerTest")) with FreeSpecLike with BeforeAndAfterAll {
  import ServiceHandler.State

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  val serviceName = "test-service"

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
    ""
    val serviceHandler = system.actorOf(ServiceHandler.props)


  }

}
