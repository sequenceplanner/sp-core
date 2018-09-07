package spgui.circuit

import diode._
import java.util.UUID

import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.vdom.VdomElement
import sp.domain.SPValue
import spgui.modal.{ModalResult}
import spgui.theming.Theming.Theme
import sp.domain.StructNode

// state
case class SPGUIModel(
                       presets: DashboardPresets = DashboardPresets(),
                       openWidgets: OpenWidgets = OpenWidgets(),
                       globalState: GlobalState = GlobalState(),
                       widgetData: WidgetData = WidgetData(Map()),
                       settings: Settings = Settings(),
                       draggingState: DraggingState = DraggingState(),
                       modalState: ModalState = ModalState()
)
case class OpenWidgets(xs: Map[UUID, OpenWidget] = Map())
case class OpenWidget(id: UUID, layout: WidgetLayout, widgetType: String)
case class WidgetLayout(x: Int, y: Int, w: Int, h: Int, collapsedHeight: Int = 1)

case class DashboardPresets(xs: Map[String, DashboardPreset] = Map())
case class DashboardPreset(widgets: OpenWidgets = OpenWidgets(), widgetData: WidgetData = WidgetData(Map()))

case class GlobalState(
  currentModel: Option[UUID] = None,
  selectedItems: List[UUID] = List(),
  userID: Option[UUID] = None,
  clientID: UUID = UUID.randomUUID(),
  attributes: Map[String, SPValue] = Map()
)
case class WidgetData(xs: Map[UUID, SPValue])

case class Settings(
  theme: Theme = Theme(),
  showHeaders: Boolean = true 
)

case class DropEventData(struct: StructNode, targetId: UUID )

case class DraggingState(
  target: Option[UUID] = None,
  dragging: Boolean = false,
  renderStyle: String = "",
  data: String = "",
  latestDropEvent: Option[DropEventData] = None 
)

case class ModalState(
                       modalVisible: Boolean = false,
                       title: String = "",
                       component: Option[(ModalResult => Callback) => VdomElement] = None,
                       onComplete: Option[ModalResult => Callback] = None
                     )

// actions
case class AddWidget(widgetType: String, width: Int = 2, height: Int = 2, id: UUID = UUID.randomUUID()) extends Action
case class CloseWidget(id: UUID) extends Action
case class CollapseWidgetToggle(id: UUID) extends Action
case object CloseAllWidgets extends Action
case class RecallDashboardPreset(preset: DashboardPreset) extends Action
case class UpdateLayout(id: UUID, newLayout: WidgetLayout) extends Action
case class SetLayout(layout: Map[UUID, WidgetLayout]) extends Action

case class AddDashboardPreset(name: String) extends Action
case class RemoveDashboardPreset(name: String) extends Action
case class SetDashboardPresets(presets: Map[String, DashboardPreset]) extends Action

case class UpdateWidgetData(id: UUID, data: SPValue) extends Action

case class UpdateGlobalAttributes(key: String, value: SPValue) extends Action
case class UpdateGlobalState(state: GlobalState) extends Action

case class SetTheme(theme: Theme) extends Action
case object ToggleHeaders extends Action

case class SetDraggableRenderStyle(style:String) extends Action
case class SetDraggableData(data: String) extends Action
case class SetCurrentlyDragging(enabled: Boolean) extends Action 
case class SetDraggingTarget(id: UUID) extends Action
case object UnsetDraggingTarget extends Action
case class DropEvent(struct: StructNode, target: UUID) extends Action

case class OpenModal(title: String = "", component: (ModalResult => Callback) => VdomElement, onComplete: ModalResult => Callback) extends Action
case object CloseModal extends Action

// used when failing to retrieve a state from browser storage
object InitialState {
  def apply() = SPGUIModel()
}
