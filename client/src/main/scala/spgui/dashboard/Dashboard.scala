package spgui.dashboard

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import diode.react.ModelProxy

import scala.scalajs.js
import js.JSConverters._
import org.scalajs.dom.window
import spgui.{SPWidgetBase, WidgetList}
import spgui.circuit.{LayoutsChanged, OpenWidget, SPGUICircuit}
import spgui.dashboard.ReactGridLayout.LayoutElement
import spgui.toHtml

object Dashboard {
  import spgui.dashboard.{DashboardCSS => css}
  case class Props(proxy: ModelProxy[List[OpenWidget]])
  case class State(width: Int)

  val cols = 12

  val currentlyDragging = SPGUICircuit.zoom(_.draggingState.dragging)

  class Backend($: BackendScope[Props, State]) {
    def render(props: Props, state: State) = {

      window.onresize = _ => $.modState(_.copy(width = window.innerWidth.toInt)).runNow()

      val openWidgets = props.proxy.value

      val widgets = for  {
        widgetData <- openWidgets
        widget <- WidgetList.list.find(_.name == widgetData.widgetType)
      } yield {
        <.div(
          DashboardItem(widget.render(SPWidgetBase(widgetData.id)),
            widgetData.widgetType,
            widgetData.id,
            widgetData.layout.h
          ),
          ^.key := widgetData.id.toString
        )
      }

      val currentLayout = openWidgets
        .map(widget => (widget.id.toString, widget.layout))
        .map { case (id, l) => LayoutElement(key = id, x = l.x, y = l.y, w = l.w, h = l.h) }
        .toJSArray

      val gridLayout = ReactGridLayout(
        layout = currentLayout.toJSArray,
        width = state.width,
        cols = cols,
        draggableHandle = "." + css.widgetPanelHeader.htmlClass,
        onLayoutChange = layouts => SPGUICircuit.dispatch(LayoutsChanged(layouts.toSeq)),
        children = widgets.toVdomArray
      )

      val disableDropzone = ^.className := "dropzonesDisabled"

      <.div(
        css.mainContainer,
        disableDropzone.unless(currentlyDragging.value),
        gridLayout
      )
    }

    def didMount() = {
      $.modState(_.copy(width = window.innerWidth.toInt))
    }
  }

  private val component = ScalaComponent.builder[Props]("Dashboard")
    .initialState(State(0))
    .renderBackend[Backend]
    .componentDidMount(ctx => ctx.backend.didMount())
    .build

  def apply(proxy: ModelProxy[List[OpenWidget]]) = component(Props(proxy))
}