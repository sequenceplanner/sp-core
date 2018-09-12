import japgolly.scalajs.react.vdom.TagMod
import japgolly.scalajs.react.vdom.html_<^._
import scalacss.internal.StyleA


package object spgui {
  // This allows Scala.JS to choose dev settings during fastOptJS, and prod settings during fullOptJS.
  val CssSettings = scalacss.devOrProdDefaults
  implicit def toHtml(a: StyleA): TagMod = ^.className := a.htmlClass

  def bootstrap(classes: String*) = ^.className := classes.mkString(" ")
}