package spgui.dragging

import scalacss.DevDefaults._
import spgui.theming.Theming

object DraggingCSS extends Theming.SPStyleSheet {
  import dsl._

  val dragElement = style(
    position.absolute,
    userSelect:= "none",
    pointerEvents:= "none"
  )

  val overlay = style(
    unsafeRoot("body")(
      userSelect := "none"
    ),
    height(100.%%),
    width(100.%%),
    position.absolute,
    top(0.px)
  )

  val hidden = style(display.none)

  this.addToDocument()
}
