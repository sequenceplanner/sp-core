package spgui.components

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.vdom.all.aria

object SPNavbarElements{
  def button(text:String, onClick: Callback): VdomNode =
    <.li(
      <.a(text,
        ^.onClick --> onClick,
        ^.className := SPNavbarElementsCSS.clickable.htmlClass,
        ^.className := SPNavbarElementsCSS.button.htmlClass
      )
    )

  def button(text:String, icon:VdomNode, onClick: Callback): VdomNode =
    <.li(
      <.a(
        <.span(text, ^.className:= SPWidgetElementsCSS.textIconClearance.htmlClass),
        icon,
        ^.onClick --> onClick,
        ^.className := SPNavbarElementsCSS.clickable.htmlClass,
        ^.className := SPNavbarElementsCSS.button.htmlClass
      )
    )

  def button(icon:VdomNode, onClick: Callback): VdomNode =
    <.li(
      <.a(icon,
        ^.onClick --> onClick,
        ^.className := SPNavbarElementsCSS.clickable.htmlClass,
        ^.className := SPNavbarElementsCSS.button.htmlClass
      )
    )

  def dropdown(text: String, contents: Seq[TagMod]): VdomElement =
    <.li(
      ^.className := SPNavbarElementsCSS.dropdownRoot.htmlClass,
      ^.className := "navbar-dropdown",
      <.a(
        <.span(text, ^.className:= SPNavbarElementsCSS.textIconClearance.htmlClass),
        Icon.caretDown,
        VdomAttr("data-toggle") := "dropdown",
        ^.id:="something",
        ^.className := SPNavbarElementsCSS.clickable.htmlClass,
        ^.className := "nav-link dropdown-toggle"
      ),
      <.ul(
        contents.toTagMod,
        ^.className := SPNavbarElementsCSS.dropDownList.htmlClass,
        ^.className := "dropdown-menu"
      )
    )

  def dropdownElement(text: String, icon: VdomNode, onClick: Callback): VdomNode = 
    <.li(
      ^.className := SPNavbarElementsCSS.dropdownElement.htmlClass,
      <.span(icon, ^.className := SPNavbarElementsCSS.textIconClearance.htmlClass),
      text,
      ^.onClick --> onClick
    )

  def dropdownElement(text: String, onClick: Callback): VdomNode =
    <.li(
      ^.className := SPNavbarElementsCSS.dropdownElement.htmlClass,
      text,
      ^.onClick --> onClick
    )

  def dropdownElement(content: TagMod, onClick: Callback): VdomNode =
    <.li(
      ^.className := SPNavbarElementsCSS.dropdownElement.htmlClass,
      content,
      ^.onClick --> onClick
    )

  def dropdownElement(content: TagMod): VdomNode =
    <.li(
      ^.className := SPNavbarElementsCSS.dropdownElement.htmlClass,
      content
    )

  object TextBox {
    case class Props(contentText: String, placeholderText: String, onChange: String => Callback )
    case class State(text: String = "")

    class Backend($: BackendScope[Props, State]) {
      def render(p:Props, s: State) =
        <.span(
          ^.className := "input-group",
          <.input(
            ^.className := "form-control",
            ^.placeholder := p.placeholderText,
            ^.value := p.contentText,
            ^.aria.describedBy := "basic-addon1",
            ^.onChange ==> onFilterTextChange(p)
          )
        )
      def onFilterTextChange(p:Props)(e: ReactEventFromInput): Callback =
        e.extract(_.target.value)(v => (p.onChange(v)))
    }

    private val component = ScalaComponent.builder[Props]("SPTextBox")
      .initialState(State())
      .renderBackend[Backend]
      .build

    // atm broken; gives funny behaviour where you can just enter one letter that you cannot see
    //def apply(placeholderText: String, onChange: String => Callback): VdomElement =
    //  component(Props("", placeholderText, onChange))

    def apply(contentText: String, placeholderText: String, onChange: String => Callback): VdomElement =
      component(Props(contentText, placeholderText, onChange))
  }
}


