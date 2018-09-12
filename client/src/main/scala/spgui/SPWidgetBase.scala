package spgui

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

import spgui.circuit._
import sp.domain._
import java.util.UUID

case class SPWidgetBase(id: UUID) {

  def updateWidgetData(data: SPValue): Unit = {
    SPGUICircuit.dispatch(UpdateWidgetData(id, data))
  }

  def getWidgetData: SPValue = {
    SPGUICircuit.zoom(_.widgetData.xs.get(id)).value.getOrElse(SPValue.empty)
  }

  def updateGlobalState(state: GlobalState): Unit = {
    SPGUICircuit.dispatch(UpdateGlobalState(state))
  }

  def openNewWidget(widgetType: String, initialData: SPValue = SPValue.empty): Unit = {
    val w = AddWidget(widgetType = widgetType)
    val d = UpdateWidgetData(w.id, initialData)

    SPGUICircuit.dispatch(d)
    SPGUICircuit.dispatch(w)
  }

  def closeSelf(): Unit = SPGUICircuit.dispatch(CloseWidget(id))

}

object SPWidget {
  case class Props(base: SPWidgetBase, renderWidget: SPWidgetBase => VdomElement)
  private val component = ScalaComponent.builder[Props]("SpWidgetComp")
    .render_P(p => p.renderWidget(p.base))
    .build

  def apply(renderWidget: SPWidgetBase => VdomElement): SPWidgetBase => VdomElement =
    base => component(Props(base, renderWidget))
}