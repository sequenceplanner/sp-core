package spgui.dashboard

import sp.domain.Logic._
import play.api.libs.json._
import sp.domain
import sp.domain._
import spgui.circuit.{AddDashboardPreset, DashboardPreset, SPGUICircuit, SetDashboardPresets}
import spgui.communication.BackendCommunication
import spgui.menu.SPMenu

import scala.util.Try

/**
  * Created by alfredbjork on 2018-04-05.
  */
abstract class AbstractDashboardPresetsHandler {

  // Add the menu component to the menu and start subscribing preset messages
  SPMenu.addNavElem(connectedMenuComponent(p => DashboardPresetsMenu(p, AbstractDashboardPresetsHandler.this.requestPresets)).vdomElement)
  private val obs = BackendCommunication.getMessageObserver(handleMsg, dashboardPresetsTopic)

  // Let SPGUICircuit know that we exist
  SPGUICircuit.dashboardPresetHandler = Some(AbstractDashboardPresetsHandler.this)

  //requestPresets() // Get initial state for presets

  private def connectedMenuComponent = {
    SPGUICircuit.connect(m => DashboardPresetsMenu.ProxyContents(m.presets, m.openWidgets, m.widgetData))
  }

  protected final def updateGUIState(presets: Map[String, DashboardPreset]): Unit = {
    SPGUICircuit.dispatch(SetDashboardPresets(presets))
    println("GUI state updated to: " + presets)
  }

  protected final def fromJson(json: String): DashboardPreset = {
    import spgui.circuit.JsonifyUIState._
    domain.fromJsonAs[DashboardPreset](json).getOrElse(DashboardPreset())
  }

  protected final def toJson(preset: DashboardPreset): String = {
    import spgui.circuit.JsonifyUIState._
    domain.toJson(preset)
  }

  protected final def sendMsg[S](from: String, to: String, payload: S)(implicit fjs: domain.JSWrites[S]) =
    BackendCommunication.publish(SPMessage.make[SPHeader, S](SPHeader(from = from, to = to), payload), dashboardPresetsTopic)

  /**
    * Returns the topic to be used for communication between frontend and backend regarding dashboard presets
    * @return String
    */
  protected def dashboardPresetsTopic: String

  /**
    * Should process messages that comes on the dashboardPresetsTopic and update the
    * GUIModel according to incoming messages.
    * @param msg SPMessage
    */
  def handleMsg(msg: SPMessage): Unit

  /**
    * Should ask the backend for presets so that handleMsg can do its job.
    * This is called when the GUI is instantiated but can also be called at other times.
    */
  def requestPresets(): Unit

  /**
    * Should take the given preset and send it to the backend for persistent storage
    * @param presets
    */
  def storePresetToPersistentStorage(name: String, preset: DashboardPreset): Unit

  /**
    * Should tell persistent storage to remove the preset corresponding to the given name
    * @param name
    */
  def removePresetFromPersistentStorage(name: String): Unit

}