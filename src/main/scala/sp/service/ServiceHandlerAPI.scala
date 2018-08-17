package sp.service

import com.sksamuel.avro4s.SchemaFor
import sp.domain.SchemaLogic._
import sp.domain._

object ServiceHandlerAPI {
  case class ServiceHandlerRequest(request: APIServiceHandler.Request)
  case class ServiceHandlerResponse(response: APIServiceHandler.Response)

  val req = SchemaFor[ServiceHandlerRequest]
  val resp = SchemaFor[ServiceHandlerResponse]
  val k = req()

  val apischema = makeMeASchema(req(), resp())

  val attributes: APISP.StatusResponse = APISP.StatusResponse(
    service = APIServiceHandler.service,
    instanceID = Some(ID.newID),
    instanceName = "",
    tags = List("service", "core"),
    api = apischema,
    version = 1,
    topicRequest = APIServiceHandler.topicRequest,
    topicResponse = APIServiceHandler.topicResponse,
    attributes = SPAttributes.empty
  )
}


