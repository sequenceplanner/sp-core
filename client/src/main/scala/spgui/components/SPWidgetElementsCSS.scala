package spgui.components

import scalacss.DevDefaults._
import spgui.theming.Theming.SPStyleSheet

object SPWidgetElementsCSS extends SPStyleSheet {
  import dsl._

  val button = style(
    borderRadius(0.px),
    borderColor(_rgb(theme.value.widgetButtonBorderColor)),
    backgroundColor(_rgb(theme.value.widgetButtonBackgroundColor)),
    color(_rgb(theme.value.defaultTextColor)).important
  )

  val dropDownList = style(
    borderRadius(0.px),
    cursor.pointer,
    backgroundColor(_rgb(theme.value.widgetButtonBackgroundColor)),
    color(_rgb(theme.value.defaultTextColor))
  )

  val defaultMargin = style(margin(2.px))

  val dropdownRoot = style (
    unsafeChild(".dropdown-menu")(
      padding(4.px)
    ),
    unsafeChild("span.open > button > i")(
      transform := "rotate(180deg)" // flip the caret
    ),
    unsafeChild("span.open > button")(
      borderColor(_rgb(theme.value.spOrange)),
      backgroundColor(_rgb(theme.value.widgetButtonBackgroundHoverColor))
    )
  )

  val dropdownOuter = style(
    display.inlineBlock
  )

  val textIconClearance = style(marginRight(6.px) )

  val clickable = style(
    cursor.pointer,
    userSelect := "none",
    //backgroundColor.transparent,
    listStyle := "none",
    
    &.hover (
      borderColor(_rgb(theme.value.spOrange)),
      backgroundColor(_rgb(theme.value.widgetButtonBackgroundHoverColor))
    )
  )

  val textBox = style(
    &.focus(
      boxShadow := "inset 0px 0px 0px #000000",
      border :=! "1px solid " + _rgb(theme.value.spOrange).toString
      //transition := "border-color ease-in-out 0s, box-shadow ease-in-out 0s"
    )
  )

  val dropdownElement = style(
    padding(4.px),
    &.hover(
      backgroundColor(_rgb(theme.value.widgetButtonBackgroundHoverColor)).important
    )
  )

  val hidden = style(
    visibility.hidden
  )

  val dropZoneContext = style(
    position.absolute,
    zIndex(100),
    height(100.%%),
    width(100.%%),
    userSelect:="none"
  )

  val dropZone = style(
    position.absolute,
    zIndex(101),
    opacity:= "0.5",
    userSelect:="none"
  )

  val dropZoneUnzisedOuter = style(
    display.tableCell,
    position.relative
  )

  val fillParent = style(
    width(100.%%),
    height(100.%%)
  )

  val blue = style(
    backgroundColor:= "blue"
  )

  val disableDropzones = style(
    unsafeRoot( ".dropzonesDisabled ." + dropZone.htmlClass)(
      visibility.hidden
    )
  )


  this.addToDocument()
}
