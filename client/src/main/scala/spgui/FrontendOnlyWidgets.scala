package spgui

import japgolly.scalajs.react.vdom.html_<^.VdomElement
import spgui._

/**
  *  All these widgets runnable without a backend
  */
object FrontendOnlyLoadingWidgets {

  type Widget = (String, SPWidgetBase => VdomElement, Int, Int)

  val sp =
    List[Widget](
      //      ("Grid Test",                   spgui.dashboard.GridTest(),                    5, 5),
      //      ("Widget Injection",            widgets.injection.WidgetInjectionTest(),       3, 4),
      //      ("DragDrop Example",            widgets.examples.DragAndDrop(),                3, 4),
      //      ("Widget with json",            widgets.examples.WidgetWithJSON(),             3, 4),
      //      ("PlcHldrC",                    PlaceholderComp(),                             3, 4),
      //      ("SPWBTest",                    SPWidgetBaseTest(),                            3, 4),
      //      ("Widget with data",            widgets.examples.WidgetWithData(),             3, 4),
      //      ("D3Example",                   widgets.examples.D3Example(),                  3, 4),
      //      ("D3ExampleServiceWidget",      widgets.examples.D3ExampleServiceWidget(),     3, 4),
      //      ("ExampleServiceWidget",        widgets.examples.ExampleServiceWidget(),                        3, 4),
      //      ("ExampleServiceWidgetState",   widgets.examples.ExampleServiceWidgetState(),                   3, 3),
      ("Item explorer",               widgets.itemexplorer.ItemExplorer(),           3, 4),
      ("Item explorer tree",          widgets.itemtree.ItemExplorer(), 2, 4),
      ("Gantt Examples",              widgets.gantt.GanttExamples(), 10, 5),
      ("Live Gantt Example",          widgets.gantt.LiveGanttExample(), 10, 5),
      ("ServiceList",                 widgets.services.ServiceListWidget(),          3, 4),
      //("SopMaker",                    widgets.sopmaker.SopMakerWidget(),             3, 4)
    )


  def loadWidgets: Unit = {
    WidgetList.addWidgets(sp)
  }
}
