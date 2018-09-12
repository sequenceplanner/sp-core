package spgui

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import spgui.GlobalCSS
import spgui.circuit.{SPGUICircuit, SPGUIModel}
import spgui.menu.SPMenu
import spgui.dashboard.Dashboard
import spgui.dragging.Dragging
import spgui.modal.Modal

object Layout {
  def connect[A <: AnyRef, B <: VdomElement](f: SPGUIModel => A) = SPGUICircuit.connect(f)

  val component = ScalaComponent.builder[Unit]("Layout")
    .render(_ =>
      <.div(
        ^.className := GlobalCSS.layout.htmlClass,
        connect(_.modalState)(Modal(_)),
        connect(_.settings)(SPMenu(_)),
        connect(s => s.openWidgets.xs.values.toList)(Dashboard(_)),
        Dragging.mouseMoveCapture,
        connect(_.draggingState)(Dragging(_))
      )
    )
    .build

  def apply() = component()
}