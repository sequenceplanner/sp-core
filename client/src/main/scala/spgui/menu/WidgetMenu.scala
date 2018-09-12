package spgui.menu

import japgolly.scalajs.react._
import spgui.circuit.{AddWidget, SPGUICircuit}
import spgui.WidgetList
import spgui.components.SPNavbarElements

object WidgetMenu {
  case class State(filterText: String = "")

  class Backend($: BackendScope[Unit, State]) {
    def addWidget(name: String, w: Int, h: Int): Callback = Callback {
      println(s"addWidget: $name")
      SPGUICircuit.dispatch(AddWidget(name, w, h))
    }

    def render(state: State) = {
      def capturedByFilter(s: String) = {
        println("Captured by filter")
        s.toLowerCase.contains(state.filterText.toLowerCase)
      }
      val widgets = WidgetList.list.collect { case widget if capturedByFilter(widget.name) =>
        SPNavbarElements.dropdownElement(widget.name,
          addWidget(widget.name, widget.width, widget.height)
        )
      }

      val filterBox = SPNavbarElements.TextBox(
        state.filterText,
        "Find widget...",
        s => $.modState(_.copy(filterText = s))
      )

      SPNavbarElements.dropdown("New widget", filterBox :: widgets)
    }

  }

  private val component = ScalaComponent.builder[Unit]("WidgetMenu")
    .initialState(State())
    .renderBackend[Backend]
    .build

  def apply() = component()
}
