package sp.models

import akka.NotUsed
import akka.actor._
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import sp.domain._
import sp.domain.Logic._
import sp.service._
import akka.persistence.{PersistentActor, _}
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep}
import play.api.libs.json.Json
import sp.models.APIModel.SPItems
import akka.stream.scaladsl.{Sink, Source}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global


object ModelActor {
  def props(cm: APIModelMaker.CreateModel) = Props(classOf[ModelActor], cm)
}

class ModelActor(val modelSetup: APIModelMaker.CreateModel)
  extends PersistentActor  with
    ModelLogicRevert with
    ActorLogging with
    ServiceCommunicationSupport with
    MessageBussSupport
{
  val id: ID = modelSetup.id
  override def persistenceId = id.toString

  subscribe(APIModel.topicRequest)
  triggerServiceRequestComm(serviceResp)
  sendAnswer(SPHeader(from = id.toString), getModelInfo)


  def receiveCommand = {
    case x: String if sender() != self =>
      for {
        m <- SPMessage.fromJson(x)
        h <- m.getHeaderAs[SPHeader] if h.to == modelSetup.id.toString || h.to == APIModel.service
        b <- m.getBodyAs[APIModel.Request]
      } yield {
        val updH = h.copy(from = id.toString, to = h.from)
        sendAnswer(updH, APISP.SPACK())
        b match {
          case k: APIModel.PutItems if h.to == id.toString =>
            val res = putItems(k.items, k.info)
            handleModelDiff(res, updH)
          case k: APIModel.DeleteItems if h.to == id.toString =>
            val res = deleteItems(k.items, k.info)
            handleModelDiff(res, updH)
          case k: APIModel.UpdateModelAttributes if h.to == id.toString =>
            val res = updateAttributes(k.name, k.attributes)
            handleModelDiff(res, updH)
          case k: APIModel.RevertModel if h.to == id.toString =>
            val res = revertModel(k.toVersion, context.system, persistenceId) // should start at version 0
            handleModelDiff(res, updH)
          case APIModel.ExportModel =>
            sendAnswer(updH, getTheModelToExport)
          case APIModel.GetModelInfo =>
            sendAnswer(updH, getModelInfo)
          case APIModel.GetModelHistory =>
            val res = getModelHistory
            sendAnswer(updH, getModelHistory)
          case APIModel.GetItems(xs) =>
            val res = xs.flatMap(state.idMap.get)
            sendAnswer(updH, APIModel.SPItems(res.toList))
          case APIModel.GetItemList(from, size, filter) =>
            val res = state.items.slice(from, from + size)
            val appliedFilter = res.filter{item =>
              val nameM = filter.regexName.isEmpty || item.name.toLowerCase.matches(filter.regexName)
              val typeM = filter.regexType.isEmpty || item.getClass.getSimpleName.toLowerCase.matches(filter.regexType)
              nameM && typeM
            }
            sendAnswer(updH, SPItems(appliedFilter))
          case APIModel.GetItem(itemID) =>
            state.idMap.get(itemID) match {
              case Some(r) => sendAnswer(updH, APIModel.SPItem(r))
              case None => sendAnswer(updH, APISP.SPError(s"item $itemID does not exist"))
            }
          case APIModel.GetStructures   =>
            val res = state.items.filter(_.isInstanceOf[Struct])
            sendAnswer(updH, APIModel.SPItems(res))
          case APIModel.GetAllItemInfo => {
            val info = state.items.map(i => APIModel.SPItemInfo(i.id, i.name,idableType(i)))
            sendAnswer(updH, APIModel.SPItemInfoList(info))
          }
          case APIModel.GetItemInfo(ids) => {
            val info = state.items.filter(idab => ids.contains(idab.id)).map(i => APIModel.SPItemInfo(i.id, i.name, idableType(i)))
            sendAnswer(updH, APIModel.SPItemInfoList(info))
          }
          case APIModel.GetItemAttributes(ids) => {
            val attr = ids.flatMap(state.idMap.get).map(_.attributes)
            sendAnswer(updH, APIModel.SPAttributesList(attr))
          }
          case x if h.to == id.toString =>
            println(s"Model $id got something not implemented: ${x}")
          case _ =>
        }

        sendAnswer(updH, APISP.SPDone())

      }
  }

  def idableType(idable:IDAble): String = idable match {
    case Operation(_,_,_,_) => "Operation"
    case Thing(_,_,_) => "Thing"
    case SOPSpec(_,_,_,_) => "SOPSpec"
    case SPSpec(_,_,_) => "SPSpec"
    case SPResult(_,_,_) => "SPResult"
    case SPState(_,_,_,_) => "SPState"
    case Struct(_,_,_,_) => "Struct"
  }

  def serviceResp = ModelInfo.attributes.copy(
    instanceName = id.toString,
    instanceID = Some(id),
    attributes = SPAttributes("modelInfo" -> getModelInfo)
  )

  override def receiveRecover: Receive = {
    case x: String =>
      val diff = SPAttributes.fromJsonGetAs[ModelDiff](x)
      diff.foreach(updateState)
  }

  def handleModelDiff(d: Option[ModelDiff], h: SPHeader) = {
    d.foreach{diff =>
      persist(SPValue(diff).toJson){json =>
        val res = makeModelUpdate(diff)
        sendAnswer(h, res)
      }
    }
  }

  override def postStop() = {
    println("MODEL remove: " + id)
    super.postStop()
  }

  def sendAnswer(h: SPHeader, b: APISP) = publish(APIModel.topicResponse, SPMessage.makeJson(h, b))
  def sendAnswer(h: SPHeader, b: APIModel.Response) = publish(APIModel.topicResponse, SPMessage.makeJson(h, b))
  //def sendEvent(h: SPHeader, b: APIModel.Response) = publish(APISP.spevents, SPMessage.makeJson(h.copy(to = ""), b))
}


trait ModelLogicRevert {
  val id: ID
  val modelSetup: APIModelMaker.CreateModel

  case class ModelState(version: Int, idMap: Map[ID, IDAble], history: Map[Int, SPAttributes], attributes: SPAttributes, name: String){
    lazy val items = idMap.values.toList
  }

  case class ModelDiff(model: ID,
                       updatedItems: List[IDAble],
                       deletedItems: List[IDAble],
                       diffInfo: SPAttributes,
                       fromVersion: Long,
                       name: String,
                       modelAttr: SPAttributes = SPAttributes().addTimeStamp
                      )

  object ModelDiff {
    implicit lazy val fModelDiff: JSFormat[ModelDiff] = deriveFormatSimple[ModelDiff]
  }

  val initialHistory = SPAttributes("info"->s"Model created.")
  var state = ModelState(0, Map(), Map(0 -> initialHistory), modelSetup.attributes, modelSetup.name)


  def putItems(items: List[IDAble], info: SPAttributes) = {
    createDiffUpd(items, info)
  }

  def deleteItems(items: List[ID], info: SPAttributes) = {
    createDiffDel(items.toSet, info)
  }

  def updateAttributes(name: Option[String], attr: Option[SPAttributes]) = {
    val uN = name.getOrElse(state.name)
    val uA = attr.getOrElse(state.attributes)
    if (uN != state.name && uA != state.attributes) None
    else Some(ModelDiff(
      model = id,
      updatedItems = List(),
      deletedItems = List(),
      diffInfo = SPAttributes("info"->s"model attributes updated"),
      fromVersion = state.version,
      name = uN,
      modelAttr = uA))
  }

  def getTheModelToExport = APIModel.ModelToExport(state.name, id, state.version, state.attributes, state.items)
  def getModelInfo = APIModel.ModelInformation(state.name, id, state.version, state.items.size, state.attributes)
  def getModelHistory = APIModel.ModelHistory(id, state.history.toList.sortWith(_._1 > _._1))


  def makeModelUpdate(diff: ModelDiff) = {
    updateState(diff)
    APIModel.ModelUpdate(id, state.version, state.items.size, diff.updatedItems, diff.deletedItems.map(_.id), diff.diffInfo)
  }


  def createDiffUpd(ids: List[IDAble], info: SPAttributes): Option[ModelDiff] = {
    val upd = ids.flatMap{i =>
      val xs = state.idMap.get(i.id)
      if (!xs.contains(i)) Some(i)
      else None
    }
    if (upd.isEmpty ) None
    else {
      val updInfo = if (info.values.isEmpty) SPAttributes("info"->s"updated: ${upd.map(_.name).mkString(",")}") else info
      Some(ModelDiff(id,
        upd,
        List(),
        updInfo,
        state.version,
        state.name,
        state.attributes.addTimeStamp))
    }
  }

  def createDiffDel(delete: Set[ID], info: SPAttributes): Option[ModelDiff] = {
    val upd = updateItemsDueToDelete(delete)
    val modelAttr = sp.domain.logic.IDAbleLogic.removeIDFromAttribute(delete, state.attributes)
    val del = (state.idMap.filter(kv =>  delete.contains(kv._1))).values
    if (delete.nonEmpty && del.isEmpty) None
    else {
      val updInfo = if (info.values.isEmpty) SPAttributes("info"->s"deleted: ${del.map(_.name).mkString(",")}") else info
      Some(ModelDiff(id, upd, del.toList, updInfo, state.version, state.name, modelAttr.addTimeStamp))
    }
  }

  def updateState(diff: ModelDiff) = {
    if (state.version != diff.fromVersion)
      println(s"MODEL DIFF UPDATE is not in phase: diffState: ${diff.fromVersion}, current: ${state.version}")

    val version = state.version + 1
    val diffMap = state.history + (version -> diff.diffInfo)
    val idm = diff.updatedItems.map(x=> x.id -> x).toMap
    val dels = diff.deletedItems.map(_.id).toSet
    val allItems = (state.idMap ++ idm) filterKeys(id => !dels.contains(id))
    state = ModelState(version, allItems, diffMap, diff.modelAttr, diff.name)
  }


  def updateItemsDueToDelete(dels: Set[ID]): List[IDAble] = {
    val items = state.idMap.filterKeys(k => !dels.contains(k)).values
    sp.domain.logic.IDAbleLogic.removeID(dels, items.toList)
  }



  def revertModel(version: Long, actorSystem: ActorSystem,persistenceId : String) : Option[ModelDiff] = {
    val queries = PersistenceQuery(actorSystem).readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier)
    val source: Source[EventEnvelope, NotUsed] = queries.currentEventsByPersistenceId(persistenceId, 0, version) // create a source containing events given by persist to the desired model version

    implicit val mat = ActorMaterializer()(actorSystem)

    def calculateNewState(s: ModelState, diff: ModelDiff) = { // update the new state with the modeldiff of the persist event
      val version = s.version + 1
      val idm = diff.updatedItems.map(x => x.id -> x).toMap
      val dels = diff.deletedItems.map(_.id).toSet
      val allItems = (s.idMap ++ idm) filterKeys (id => !dels.contains(id))
      val diffMap = state.history + (version -> diff.diffInfo)
      ModelState(version, allItems, diffMap, diff.modelAttr, diff.name)
    }

    val tmpState = ModelState(0, Map(), Map(0 -> initialHistory), modelSetup.attributes, modelSetup.name)

    val getModelState = Sink.fold[ModelState, ModelDiff](tmpState)(calculateNewState(_, _)) // the left argument of calculateNewState will be updated with the new result bc of folding
    val toModelDiff = Flow[EventEnvelope].map(e => Json.parse(e.event.toString).as[ModelDiff]) // transforms the source to ModelDiff
    val modelStateStream = source.via(toModelDiff).toMat(getModelState)(Keep.right)
    val futureModelState = modelStateStream.run() // gets a future which should contain a model state created by the "sum" of ModelDiffs

    var result = Option(ModelDiff(id,List(),List(),SPAttributes("info" -> ("reverted back to version " + version)), state.version, state.name, SPAttributes().addTimeStamp))

    val f = futureModelState.map(newState => { // Create a new model diff by comparing new and old state

      // Determine which items have changed or been deleted between current and new state
      var del = List[IDAble]()
      var upd = List[IDAble]()
      newState.idMap.foreach(i => if (!(state.idMap.keySet.contains(i._1) && state.idMap(i._1) == i._2)) upd :+= i._2)
      state.idMap.foreach(i => if (!newState.idMap.keySet.contains(i._1)) del :+= i._2)

      // update the current state with the difference to achieve the new state.
      result = Option(ModelDiff(
        id,
        upd,
        del,
        SPAttributes("info" -> ("reverted back to version " + version)),
        state.version,
        newState.name,
        SPAttributes().addTimeStamp
      ))
    })
    Await.ready(f, 10000 millis) // wait until future is ready
    result
  }
}
