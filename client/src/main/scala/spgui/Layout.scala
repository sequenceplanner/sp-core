package spgui

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import spgui.circuit.SPGUICircuit
import spgui.menu.SPMenu
import spgui.dashboard.Dashboard
import spgui.dragging.Dragging
import spgui.modal.Modal

object Layout {
  val widgetsConnection = SPGUICircuit.connect(x => (x.openWidgets.xs, x.globalState))
  val menuConnection = SPGUICircuit.connect(x => (x.settings))
  val draggingConnection = SPGUICircuit.connect(x => (x.draggingState))
  val modalConnection = SPGUICircuit.connect(x => (x.modalState))

  val component = ScalaComponent.builder[Unit]("Layout")
    .render(_ =>
      <.div(
        ^.className := GlobalCSS.layout.htmlClass,
        modalConnection(Modal(_)),

        menuConnection(SPMenu(_)),
        widgetsConnection(Dashboard(_)),

        Dragging.mouseMoveCapture,
        draggingConnection(Dragging(_))
      )
    )
    .build

  def apply() = component()
}
