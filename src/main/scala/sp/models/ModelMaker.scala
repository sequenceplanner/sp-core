package sp.models

import akka.actor._
import akka.persistence.{PersistentActor, RecoveryCompleted}
import sp.domain._
import sp.effect.Publish.PublishResponse
import sp.models.APIModelMaker.{CreateModel, DeleteModel, ModelList}
import sp.models.ModelMaker._
import sp.service.{MessageBussSupport, ServiceCommunicationSupport}
import sp.Text.ModelMakerText._
import sp.effect._
import sp.Util.SPMessageSyntax._

class ModelMaker(modelActorMaker: CreateModel => Props)(implicit effects: Effects) extends
  PersistentActor with
  ActorLogging with
  ServiceCommunicationSupport with
  MessageBussSupport {
  override def persistenceId = "modelMaker"

  implicit val implicitSelf = this

  val selfHeader = APIModelMaker.service

  val instanceID = ID.newID
  val serviceInfo = ModelMakerAPI.attributes.copy(instanceID = Some(instanceID))

  triggerServiceRequestComm(serviceInfo)

  // TODO: ModelMaker needs to check if there are already
  // running models and or modelmakers. Ask the serviceHandler
  // or ask on the modeltopic

  val Acknowledge = APISP.SPACK()
  val Done = APISP.SPDone()
  def Error(msg: String) = APISP.SPError(msg)

  private def onCreateModel(state: State, request: CreateModel, header: SPHeader, rawMessage: String)(onSuccess: => Unit): Unit = {
    import PublishResponse.toJsValue
    val requestExists = state.actors.contains(request.id)

    if (requestExists) effects.respond(header, Error(CannotCreateModel(request.id.toString)))
    else {
      effects.respond(header, Acknowledge)
      effects.persist(rawMessage) { _ =>
        onSuccess

        val created = APIModelMaker.ModelCreated(request.name, request.attributes, request.id)
        effects.respondN(header, created, Done)
      }
    }
  }

  private def onDeleteModel(state: State, req: DeleteModel, header: SPHeader, rawMessage: String)(onSuccess: => Unit): Unit = {
    import PublishResponse.toJsValue

    if (!state.actors.contains(req.id)) effects.respond(header, Error(CannotDeleteModel(req.id.toString)))
    else {
      effects.respond(header, Acknowledge)
      effects.persist(rawMessage) { _ =>
        onSuccess

        val res = APIModelMaker.ModelDeleted(req.id)
        effects.respondN(header, res, Done)
      }

    }
  }

  private def onGetModels(state: State, header: SPHeader): Unit = {
    import PublishResponse.toJsValue
    val body = ModelList(state.actors.keys.toList)

    effects.respondN(header, Acknowledge, body, Done)
  }

  private def onReceiveMessage(state: State): PartialFunction[Any, State] = {
    case data: String if sender() != self =>

      val isValidHeader = (h: SPHeader) => h.to == instanceID.toString || h.to == APIModelMaker.service

      SPMessage.fromJson(data)
        .getAs[SPHeader, APIModelMaker.Request]
        .ifHeader(isValidHeader)
        .mapHeader(h => SPHeader(from = selfHeader, to = h.from))
        .foreach { (responseHeader, body) =>
          body match {
            case request: APIModelMaker.CreateModel =>
              onCreateModel(state, request, responseHeader, rawMessage = data)(self ! ModelCreationPersisted(request))

            case request: APIModelMaker.DeleteModel =>
              onDeleteModel(state, request, responseHeader, data)(self ! ModelDeletionPersisted(request.id))

            case APIModelMaker.GetModels => onGetModels(state, responseHeader)
          }
        }

      state

    case ModelCreationPersisted(req) => state.actorForModel(req)
    case ModelDeletionPersisted(modelId) => state.deleteModel(modelId)

    case ModelCreationRestored(req) => state.stageRestore(req)
    case ModelDeletionRestored(modelId) => state.unstageRestore(modelId)

    case RecoveryFinished => state.commitPlaybacks
  }

  private def handleCommand(state: State): Receive = onReceiveMessage(state) andThen (s => context.become(handleCommand(s)))

  def receiveCommand: Receive = handleCommand(State(requestToActor = modelActorMaker, actorContext = context))

  def receiveRecover = {
    case data: String =>
      SPMessage.fromJson(data).flatMap(_.getBodyAs[APIModelMaker.Request]).foreach {
        case req: APIModelMaker.CreateModel => self ! ModelCreationRestored(req)
        case APIModelMaker.DeleteModel(modelId) => self ! ModelDeletionRestored(modelId)
        case _ => Unit
      }

    case RecoveryCompleted => self ! RecoveryFinished
  }
}

object ModelMaker {
  case class ModelCreationPersisted(model: CreateModel)
  case class ModelDeletionPersisted(modelId: ID)
  case object RecoveryFinished

  case class ModelCreationRestored(model: CreateModel)
  case class ModelDeletionRestored(modelId: ID)

  type Effects = PublishResponse[ModelMaker] with Persist[ModelMaker]


  private val defaultEffect = new PublishResponse[ModelMaker]
    with Persist[ModelMaker]
    with BusSupport {
    override def respond[B: JSWrites](h: SPHeader, b: B)(implicit actorRef: ModelMaker): Unit = {
      if (!busCreated) {
        initBus(actorRef)
      }

      publishOnBus(APIModelMaker.service, SPMessage.makeJson(h, b))
    }

    override def persist[A](event: A)(handler: A => Unit)(implicit ref: ModelMaker): Unit = ref.persist(event)(handler)

    /**
      * A list of topics that the bus will subscribe to.
      */
    override def busSubscriptionTopics = Seq(APIModelMaker.topicRequest)
  }

  /**
    *
    * @param actors Currently active model actors
    * @param restorePlaybacks stores messages coming from the actor being restored
    */
  case class State(
                    actors: Map[ID, ActorRef] = Map(),
                    restorePlaybacks: Map[ID, CreateModel] = Map(),
                    requestToActor: CreateModel => Props,
                    actorContext: ActorContext) { self =>
    def actorForModel(request: CreateModel): State = {
      copy(actors = actors + (request.id -> actorContext.actorOf(requestToActor(request))))
    }

    def deleteModel(modelId: ID): State = {
      actors.get(modelId).foreach(_ ! PoisonPill)
      copy(actors = actors - modelId)
    }

    def stageRestore(request: CreateModel): State = copy(restorePlaybacks = restorePlaybacks + (request.id -> request))
    def unstageRestore(modelId: ID): State = copy(restorePlaybacks = restorePlaybacks - modelId)

    def commitPlaybacks: State = restorePlaybacks.foldLeft(self) { case (s, (_, model)) => s.actorForModel(model) }
  }

  def props(maker: APIModelMaker.CreateModel => Props)(implicit effects: Effects = defaultEffect) = Props(new ModelMaker(maker)(defaultEffect))
}
