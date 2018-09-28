package spgui.bootstrap

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

/**
  * Components is a object for bootstrap components.
  */
object Components {
  /**
    *
    * @param text String to print in button
    * @param className Use bootstrap class as a string like "btn btn-primary"
    * @return Html-button styled with bootstrap
    */
  def button(text: String, className: String, onClick: Callback): VdomNode =
    <.button(
      ^.className := className,
      ^.onClick --> onClick,
      text
    )

  def primaryButton(text: String): VdomNode = button(text, "btn btn-primary", Callback.empty)
  def primaryButton(text: String, onClick: Callback): VdomNode = button(text, "btn btn-primary", onClick)
  def outlinePrimaryButton(text: String): VdomNode = button(text, "btn btn-outline-primary", Callback.empty)

  def secondaryButton(text: String): VdomNode = button(text, "btn btn-secondary", Callback.empty)
  def secondaryButton(text: String, onClick: Callback): VdomNode = button(text, "btn btn-secondary", onClick)
  def outlineSecondaryButton(text: String): VdomNode = button(text, "btn btn-outline-secondary", Callback.empty)

  def successButton(text: String): VdomNode = button(text, "btn btn-success", Callback.empty)
  def successButton(text: String, onClick: Callback): VdomNode = button(text, "btn btn-success", onClick)
  def outlineSuccessButton(text: String): VdomNode = button(text, "btn btn-outline-success", Callback.empty)

  def dangerButton(text:String): VdomNode = button(text, "btn btn-danger", Callback.empty)
  def dangerButton(text: String, onClick: Callback): VdomNode = button(text, "btn btn-danger", onClick)
  def outlineDangerButton(text:String): VdomNode = button(text, "btn btn-outline-danger", Callback.empty)

  def warningButton(text: String): VdomNode = button(text, "btn btn-warning", Callback.empty)
  def warningButton(text: String, onClick: Callback): VdomNode = button(text, "btn btn-warning", onClick)
  def outlineWarningButton(text: String): VdomNode = button(text, "btn btn-outline-warning", Callback.empty)

  def infoButton(text: String): VdomNode = button(text, "btn btn-info", Callback.empty)
  def infoButton(text: String, onClick: Callback): VdomNode = button(text, "btn btn-info", onClick)
  def outlineInfoButton(text: String): VdomNode = button(text, "btn btn-outline-info", Callback.empty)
  def outlineInfoButton(text: String, onClick: Callback): VdomNode = button(text, "btn btn-outline-info", onClick)

  def lightButton(text: String ): VdomNode = button(text, "btn btn-light", Callback.empty)
  def lightButton(text: String, onClick: Callback): VdomNode = button(text, "btn btn-light", onClick)
  def outlineLightButton(text: String ): VdomNode = button(text, "btn btn-outline-light", Callback.empty)

  def darkButton(text: String): VdomNode = button(text, "btn btn-dark", Callback.empty)
  def darkButton(text: String, onClick: Callback): VdomNode = button(text, "btn btn-dark", onClick)
  def outlineDarkButton(text: String): VdomNode = button(text, "btn btn-outline-dark", Callback.empty)

  def linkButton(text: String): VdomNode = button(text, "btn btn-link", Callback.empty)
  def linkButton(text: String, onClick: Callback): VdomNode = button(text, "btn btn-link", onClick)
  def outlineLinkButton(text: String): VdomNode = button(text, "btn btn-outline-link", Callback.empty)
}
