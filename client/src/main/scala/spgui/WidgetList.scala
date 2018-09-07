package spgui

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._


object WidgetList {

  type Widget = (String, SPWidgetBase => VdomElement, Int, Int)

  private var widgetList: List[Widget] = List()
  def addWidgets(xs: List[Widget]): Unit = {
    widgetList ++= xs
  }

  def list: List[Widget] = widgetList
  def map = list.map(t => t._1 -> (t._2, t._3, t._4)).toMap
}

object PlaceholderComp {
  val component = ScalaComponent.builder[Unit]("PlaceholderComp")
    .render(_ => <.h2("placeholder"))
    .build

  def apply() = SPWidget(spwb => component())
}


object SectionList {
  // To be loaded from the backend soon!
  val sections = List(
    "gul",
    "blå"
  )
}
