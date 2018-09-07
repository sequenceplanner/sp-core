package spgui

import org.scalajs.dom.document
import scala.scalajs.js.annotation.{JSExport,JSExportTopLevel}

/**
  *  This is only for pure frontend development, of things that don't need a backend turned on
  */
object FrontendOnlyMain extends App {
  @JSExportTopLevel("spgui.FrontendOnlyMain")
  protected def getInstance(): this.type = this

  @JSExport
  def main(): Unit = {
    FrontendOnlyLoadingWidgets.loadWidgets
    Layout().renderIntoDOM(document.getElementById("spgui-root"))
  }
}
