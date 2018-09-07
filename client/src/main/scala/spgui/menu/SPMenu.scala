package spgui.menu

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom._
import spgui.components.{Icon, SPNavbarElements}
import spgui.circuit._
import diode.react.ModelProxy
import spgui.dashboard.{AbstractDashboardPresetsHandler, DashboardPresetsMenu}
import spgui.modal.DummyModal
import spgui.modal.DummyModal.Return
import spgui.theming.Theming

object SPMenu {
  case class Props(proxy: ModelProxy[Settings], extraNavElem: Seq[VdomElement])

  class SPMenuBackend($: BackendScope[Props, Unit]){
    def render(p: Props): VdomElement = {
      <.nav(
        ^.className:= SPMenuCSS.topNav.htmlClass,
        ^.className := "navbar navbar-default",

        // navbar header: logo+toggle button
        <.div(
          ^.className := "navbar-header",
          ^.className := SPMenuCSS.navbarContents.htmlClass,
          <.a(
            ^.className := SPMenuCSS.navbarToggleButton.htmlClass,
            ^.className := "navbar-toggle collapsed",
            VdomAttr("data-toggle") := "collapse",
            VdomAttr("data-target") := "#navbar-contents",
            <.div(
              ^.className := SPMenuCSS.navbarToggleButtonIcon.htmlClass,
              Icon.bars
            )
          ),
          <.a(
            ^.className:= "navbar-brand",
            ^.className := SPMenuCSS.splogoContainer.htmlClass,
            <.div(
              ^.className := SPMenuCSS.spLogo.htmlClass
            )
          )
        ),

        // navbar contents
        <.div(
          ^.className := SPMenuCSS.navbarContents.htmlClass,
          ^.className := "collapse navbar-collapse",
          ^.id := "navbar-contents",
          <.ul(
            ^.className := "nav navbar-nav",

            WidgetMenu(),
            SPNavbarElements.dropdown(
              "Options",
              Seq(
                SPNavbarElements.dropdownElement(
                  "Toggle headers",
                  {if(p.proxy().showHeaders) Icon.toggleOn else Icon.toggleOff},
                  Callback(SPGUICircuit.dispatch(ToggleHeaders))
                ),
                Theming.themeList.map(theme =>
                  SPNavbarElements.dropdownElement(
                    theme.name,
                    {if(p.proxy().theme.name == theme.name) Icon.checkSquare else Icon.square},
                    Callback({
                      SPGUICircuit.dispatch(
                        SetTheme(
                          Theming.themeList.find(e => e.name == theme.name).get
                        )
                      )
                      org.scalajs.dom.window.location.reload() // reload the page
                    })
                  )
                ).toTagMod
              )
            ),
            p.extraNavElem.toTagMod(x => x.apply()), // Insert any additional menu items added by someone else
            SPNavbarElements.button("Close all", Callback(SPGUICircuit.dispatch(CloseAllWidgets)))
          )
        )
      )
    } 
  }

  private val component = ScalaComponent.builder[Props]("SPMenu")
    .renderBackend[SPMenuBackend]
    .build

  def apply(proxy: ModelProxy[Settings]) = component(Props(proxy, extraNavbarElements))

  private var extraNavbarElements: Seq[VdomElement] = Seq()

  /**
    * Used to add new navigataion elements in the menu bar.
    * @param xs
    */
  def addNavElem(xs: Seq[VdomElement]): Unit = extraNavbarElements ++= xs

  def addNavElem(x: VdomElement): Unit = addNavElem(Seq(x))

}