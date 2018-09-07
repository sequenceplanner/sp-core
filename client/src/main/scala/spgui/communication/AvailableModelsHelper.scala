package spgui.communication

import sp.domain._
import sp.domain.Logic._

import spgui.communication._
import spgui.communication.APIComm._

import scala.concurrent.ExecutionContext.Implicits.global

import japgolly.scalajs.react.Callback

// helps to mirror the backend state of available models (id and name)
// todo: this feels very unsafe and dirty :)
object AvailableModelsHelper {
  val from = "AvailableModelsHelper"
  import sp.models.{APIModelMaker => apimm}
  val mmcomm = new APIComm[apimm.Request, apimm.Response](apimm.topicRequest,
    apimm.topicResponse, from, apimm.service, Some(() => onUp()), Some(onChange))

  import sp.models.{APIModel => apim}
  val mcomm = new APIComm[apim.Request, apim.Response](apim.topicRequest,
    apim.topicResponse, from, apim.service, None, None)

  // dangerous state updates ahead
  private var _models: Map[ID,String] = Map()
  private var _cbs: Set[Map[ID,String] => Callback] = Set()

  def addCB(cb: Map[ID,String] => Callback) = _cbs += cb
  def removeCB(cb: Map[ID,String] => Callback) = _cbs -= cb

  private def updateCBs() = {
    _cbs.foreach(_(_models).runNow())
  }

  def onChange(header: SPHeader, body: apimm.Response): Unit = {
    synchronized {
      body match {
        case apimm.ModelCreated(name, attr, modelid) =>
          _models = _models + (modelid -> name)
        case apimm.ModelDeleted(modelid) =>
          _models = _models - modelid
        case _ =>
          _models = _models
      }
    }
    updateCBs()
  }

  def onUp(): Unit = {
    mmcomm.request(apimm.GetModels).takeFirstResponse.foreach {
      case (_, apimm.ModelList(models)) =>
        // add previously unknown models
        val newModels = models.toSet.diff(_models.keySet).toSeq
        synchronized {
          _models = _models ++ newModels.map(id=>(id->"fetching")).toMap
        }
        updateCBs()
        // ask for model names
        newModels.foreach { id =>
          mcomm.request(SPHeader(from = from, to = id.toString), apim.GetModelInfo).takeFirstResponse.foreach {
            case (_, apim.ModelInformation(name, id, _, _, _)) =>
              synchronized {
                _models = _models + (id -> name)
              }
              updateCBs()
            case _ =>
          }
        }
      case _ =>
    }
  }
}
