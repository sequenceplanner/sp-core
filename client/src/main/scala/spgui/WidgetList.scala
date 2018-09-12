package spgui

import japgolly.scalajs.react.vdom.html_<^._

object WidgetList {
  case class Widget(name: String, render: SPWidgetBase => VdomElement, width: Int, height: Int)

  private var widgetList: List[Widget] = List()
  def addWidgets(xs: List[Widget]): Unit = {
    widgetList ++= xs
  }

  def list: List[Widget] = widgetList
}
