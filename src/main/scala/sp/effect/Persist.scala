package sp.effect

trait Persist[C] {
  def persist[A](event: A)(handler: A ⇒ Unit)(implicit context: C): Unit
}
