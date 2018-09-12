package spgui.widgets.itemtree

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import spgui.{SPWidget, SPWidgetBase}
import spgui.components.DragAndDrop.{DataOnDrag, OnDataDrop}
import spgui.communication.BackendCommunication
import spgui.circuit.{GlobalState, SPGUICircuit, UpdateGlobalState}
import sp.domain._

import scalajs.js
import js.annotation.JSExportTopLevel
import java.util.UUID

import diode.react.ModelProxy


object ItemExplorer {
  import sp.models.{APIModel => api}

  // TODO temporary way of setting currentModel, currentModel-field will be moved to global state attributes
  @JSExportTopLevel("setCurrentModel")
  def setCurrentModel(modelIDString: String): Unit = {
    val id = UUID.fromString(modelIDString)
    val action = UpdateGlobalState(GlobalState(currentModel = Some(id)))
    SPGUICircuit.dispatch(action)
  }


  def extractMResponse(message: SPMessage) = for {
    h <- message.getHeaderAs[SPHeader]
    b <- message.getBodyAs[api.Response]
  } yield b

  def makeSPMessage(h: SPHeader, b: api.Request) = SPMessage.make[SPHeader, api.Request](h, b)

  case class ItemExplorerState(items: List[IDAble], modelIDFieldString: String = "modelID")

  case class Props(base: SPWidgetBase, proxy: ModelProxy[GlobalState])

  class ItemExplorerBackend($: BackendScope[Props, ItemExplorerState]) {
    val wsObs = BackendCommunication.getWebSocketStatusObserver(onWebSocketStatusChange, api.topicResponse)
    val topicHandler = BackendCommunication.getMessageObserver(onMessage, api.topicResponse)

    def onMessage(message: SPMessage): Unit = extractMResponse(message).foreach {
      case api.SPItems(items) => $.modState(_.copy(items = items)).runNow()
      case _ =>
    }

    def sendToModel(model: ID, mess: api.Request): Callback = CallbackTo[Unit] {
      val header = SPHeader(from = "ItemExplorer", to = model.toString, reply = SPValue("ItemExplorer"))
      val json = makeSPMessage(header, mess)
      BackendCommunication.publish(json, api.topicRequest)
    }

    def onWebSocketStatusChange(isOpen: Boolean): Unit = if (isOpen) {
      $.props
        .map(_.proxy.value.currentModel.foreach(modelId => sendToModel(modelId, api.GetItemList())))
        .runNow()
    }

    def render(p: Props, s: ItemExplorerState) =
      <.div(
        if (p.proxy.value.currentModel.isDefined) renderItems(s.items) else renderIfNoModel,
        OnDataDrop(str => Callback.log("dropped " + str + " on item explorer tree"))
      )

    def renderIfNoModel =
      <.div(
        "No model selected. Create a model with som items in ModelsWidget and call setCurrentModel(idString) in console to select one, then open this widget again"
      )

    def renderItems(items: List[IDAble]) =
      <.div(
        <.ul(
          items.toTagMod(idAble => <.li(idAble.name, DataOnDrag(idAble.id.toString, Callback.empty)))
          //items.toTagMod(idAble => <.li(idAble.name))
        )
      )
  }

  val connect = SPGUICircuit.connect(_.globalState)


  val itemExplorerComponent = ScalaComponent.builder[Props]("ItemExplorer")
    .initialState(ItemExplorerState(Nil))
    .renderBackend[ItemExplorerBackend]
    .build

  def apply() = SPWidget { base =>
    connect(x => itemExplorerComponent(Props(base, x)))
  }
}
