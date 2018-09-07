package spgui.dashboard

import java.util.UUID

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import diode.react.ModelProxy
import spgui.SPWidgetBase
import spgui.circuit._
import spgui.WidgetList
import spgui.dashboard.{ReactGridLayout => RGL}

import scala.scalajs.js
import js.JSConverters._
import org.scalajs.dom.window

import scala.util.Try

object Dashboard {
  case class Props(proxy: ModelProxy[(Map[UUID, OpenWidget], GlobalState)])
  case class State(width: Int)

  val cols = 12

  val currentlyDragging = SPGUICircuit.zoom(_.draggingState.dragging)

  class Backend($: BackendScope[Props, State]) {
    def render(p: Props, s: State) = {
      window.onresize = { e: org.scalajs.dom.Event =>
        $.setState(State(window.innerWidth.toInt)).runNow()
      }

      val widgets = (for {
        openWidget <- p.proxy()._1.values.toList
      } yield {

        val frontEndState = p.proxy()._2

        Try {
          <.div(
            DashboardItem(
              WidgetList.map(openWidget.widgetType)._1(
                SPWidgetBase(openWidget.id, frontEndState)
              ),
              openWidget.widgetType,
              openWidget.id,
              openWidget.layout.h
            ),
            ^.key := openWidget.id.toString
          )
        }.toOption
      }).flatten
 
      val bigLayout = (
        for {
          openWidget <- p.proxy()._1.values
        } yield {
          RGL.LayoutElement(
            i = openWidget.id.toString,
            x = openWidget.layout.x,
            y = openWidget.layout.y,
            w = openWidget.layout.w,
            h = openWidget.layout.h,
            isDraggable = true,
            isResizable = true
          )
        }
      ).toJSArray.asInstanceOf[RGL.Layout]

      val rg = RGL(
        layout = bigLayout,
        width = s.width,
        cols = cols,
        draggableHandle = "." + DashboardCSS.widgetPanelHeader.htmlClass,
        onLayoutChange = layout => {
          val changes: Map[String, RGL.LayoutElement] =
            layout.collect( {case e:RGL.LayoutElement => e.i -> e}).toMap
          
          val newLayout = p.proxy()._1.values.map(w =>  {
            val change = changes(w.id.toString)
            w.copy(
              layout = w.layout.copy(
                x = change.x,
                y = change.y,
                w = change.w,
                h = change.h,
                collapsedHeight = w.layout.collapsedHeight
              )
            )
          })

          SPGUICircuit.dispatch(
            SetLayout(newLayout.collect({case e:OpenWidget => e.id -> e.layout}).toMap))
        },
        children = widgets.toVdomArray
      )

      <.div(
        {if(!currentlyDragging.value) ^.className := "dropzonesDisabled" else EmptyVdom},
        rg
      )
    }
  }

  private val component = ScalaComponent.builder[Props]("Dashboard")
    .initialState(State( window.innerWidth.toInt))
    .renderBackend[Backend]
    .build

  def apply(proxy: ModelProxy[(Map[UUID, OpenWidget], GlobalState)]) =
    component(Props(proxy))
}
