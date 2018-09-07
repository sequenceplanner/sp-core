package spgui.modal

import spgui.menu.SPMenuCSS.{_rgb, theme}
import spgui.theming.Theming.SPStyleSheet

import scalacss.DevDefaults._

object ModalCSS extends SPStyleSheet {
  import dsl._

  val fadeScreen = style(
    height(100.%%),
    backgroundColor(rgba(0,0,0,0.3))
  )

  val window = style(
    boxShadow := "0px 2px 11px 0px rgb(0,0,0,0.5)",
    minWidth(25.em),
    top(50.%%),
    left(50.%%),
    position.absolute,
    transform := "translateY(-50%) translateX(-50%)",
    backgroundColor(_rgb(theme.value.navbarBackgroundColor)),
    color(_rgb(theme.value.defaultTextColor))
  )

  val modal_padding = 15.px

  val header = style(
    padding(modal_padding)
  )

  val close = style(
    float.right,
    fontSize(20.px)
  )

  val title = style(
    marginTop(0.px),
    maxWidth :=! "calc(100% - 1em)"
  )

  val inner = style(
    padding(modal_padding)
  )

  this.addToDocument()
}
