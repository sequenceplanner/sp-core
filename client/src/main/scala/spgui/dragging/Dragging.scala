package spgui.dragging

import java.util.UUID
import japgolly.scalajs.react._

import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.vdom.all.svg

import spgui.communication._
import sp.domain._
import scalacss.ScalaCssReact._
import scala.scalajs.js

import org.scalajs.dom.window
import org.scalajs.dom.MouseEvent
import org.scalajs.dom.document

import diode.react.ModelProxy
import spgui.circuit._
import spgui.circuit.{SetDraggableData, SetDraggableRenderStyle}
import scala.util.Try 

trait DragData
trait DropData
case class DragDropData(dragData: DragData, dropData: DropData)


object Dragging { 

  case class Props(proxy: ModelProxy[DraggingState])

  case class State(
    x: Float = 0f,
    y: Float = 0f,
    hoverTarget: UUID = null,
    isDragging: Boolean = false,
    renderStyle: String = "",
    data: Data = null
  )

  case class Data(
    label: String,
    id: UUID,
    typ: String
  )

  var setHoveringMap = Map[UUID, (Boolean) => Unit]()

  var draggingTarget: UUID = null
  var draggingData: DragData = null
  var onDrop: (DragDropData) => Unit = (d:DragDropData) => {}

  def dropzoneSubscribe( id: UUID, setHovering: (Boolean) => Unit) = {
    setHoveringMap += id -> setHovering
  }

  def dropzoneUnsubscribe(id: UUID) = {
    setHoveringMap = setHoveringMap.filter(c => c._1 != id )
    if (draggingTarget == id) draggingTarget = null
  }

  def dropzoneResubscribe(newId: UUID, previousId: UUID) = {
    if(draggingTarget == previousId) draggingTarget = newId

    setHoveringMap = setHoveringMap.map(e => {
      if(e._1 == previousId) (newId, e._2)
      else e
    })
  }

  var dropSubscribers: Map[UUID, ((DragDropData) => Unit, DropData)] = Map()

  def emitDropEvent(draggingData: DragData) = {
    //val data = DropData(draggingData, draggingTarget)
    val target = dropSubscribers.get(draggingTarget)
    target match {
      case subscriber:Some[((DragDropData) => Unit, DropData)] => {
        subscriber.map(s => {
          val dragDropData = DragDropData(draggingData, s._2)
          s._1(dragDropData) // callback to dragged element
          onDrop(dragDropData) // callback to dropzone
        })
      }
      case _ => {}
    }
  }

  def subscribeToDropEvents(id:UUID, cb: (DragDropData) => Unit, dropData: DropData) = {
    val data = (cb, dropData)
    dropSubscribers += (id -> data)
  }

  def unsubscribeToDropEvents(id: UUID) = {
    dropSubscribers -= id
  }

  trait Rect extends js.Object {
    var left: Float = js.native
    var top: Float = js.native
    var width: Float = js.native
    var height: Float = js.native
  }

  val opHeight = 80f
  val opWidth = 120f

  var updateMouse = (x:Float, y:Float) => {}

  window.onmouseup = ((e:MouseEvent) => onDragStop())

  class Backend($: BackendScope[Props, State]) {
    updateMouse = (x,y) => $.setState(State(x,y)).runNow()

    def render(state: State, props: Props) = {
      <.span(
        ^.pointerEvents.none,
        {if(!props.proxy().dragging) ^.className := DraggingCSS.hidden.htmlClass
        else EmptyVdom},
        props.proxy().renderStyle match {
          case _ =>
            <.span(
              ^.className := DraggingCSS.dragElement.htmlClass,
              ^.style := {
                var rect =  (js.Object()).asInstanceOf[Rect]
                rect.left = state.x - opWidth/2
                rect.top = state.y - opHeight/2
                rect.height = opHeight
                rect.width = opWidth
                rect
              },
              svg.svg(
                svg.svg(
                  svg.width := opWidth.toInt,
                  svg.height:= opHeight.toInt,
                  svg.x := 0,//props.proxy().x.toInt,
                  svg.y := 0,//props.proxy().y.toInt,
                  svg.rect(
                    svg.x := 0,
                    svg.y := 0,
                    svg.width := opWidth.toInt,
                    svg.height:= opHeight.toInt,
                    svg.rx := 6, svg.ry := 6,
                    svg.fill := "white",
                    svg.stroke := "black",
                    svg.strokeWidth := 1
                  ),
                  svg.svg(
                    //SopMakerCSS.opText,
                    svg.text(
                      svg.x := "50%",
                      svg.y := "50%",
                      svg.textAnchor := "middle",
                      svg.dy := ".3em", props.proxy().data
                    )
                  )
                )
              )
            )
        }
      )
    }
  }
  
  private val component = ScalaComponent.builder[Props]("Dragging")
    .initialState(State())
    .renderBackend[Backend]
    .build

  def apply(proxy: ModelProxy[DraggingState]) = component(Props(proxy))

  def setDraggingCallback(callback: (DragDropData) => Unit) {
    onDrop = callback
  }

  def onDragStart(label: String, typ: String, data: DragData, x:Float, y:Float, onDrop: (DragDropData => Unit)) {
    setDraggingData(data)
    setDraggingCallback(onDrop)
    SPGUICircuit.dispatch(SetDraggableData(label))
    SPGUICircuit.dispatch(SetCurrentlyDragging(true))
    updateMouse(x,y)
  }

  def onDragMove(x:Float, y:Float) = {
    updateMouse(x,y)
    val target = document.elementFromPoint(x, y)
    draggedOverTarget(target)
  }

  def onDragStop() = {
    if(draggingTarget!= null) emitDropEvent(draggingData)

    SPGUICircuit.dispatch(SetCurrentlyDragging(false))
   
    setDraggingData(null)
    setDraggingTarget(null)
  }

  def setDraggingStyle(style: String) = {
    SPGUICircuit.dispatch(SetDraggableRenderStyle(style))
  }

  def setDraggingData(data: DragData) = {
    draggingData = data
  }

  def setDraggingTarget(id: UUID) = {
    setHoveringMap.getOrElse(draggingTarget, (e:Boolean) => Unit)(false)
    setHoveringMap.getOrElse(id, (e:Boolean) => Unit)(true)
    draggingTarget = id
  }

  def makeUUID(id: String) = {
    Try(UUID.fromString(id)).getOrElse(null)
  }

  def draggedOverTarget(target: org.scalajs.dom.raw.Element) = {
    if(target != null) {
      val newTarget = target.id
      setDraggingTarget(makeUUID(newTarget))
    }
  }

  def mouseMoveCapture = Seq(
    ^.onMouseMove ==> {
      (e:ReactMouseEvent) => Callback{
        Dragging.onDragMove(e.pageX.toFloat, e.pageY.toFloat)
      }
    },
    (^.onTouchMoveCapture ==> {
      (e: ReactTouchEvent) => Callback ({
        val x =  e.touches.item(0).pageX.toFloat
        val y = e.touches.item(0).pageY.toFloat
        val target = document.elementFromPoint(x, y)
        Dragging.onDragMove(x, y)
      })
    })
  ).toTagMod
}
