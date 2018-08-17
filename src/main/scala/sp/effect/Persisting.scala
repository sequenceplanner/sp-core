package sp.effect

import akka.persistence.PersistentActor
import sp.effect.Kinds.Id

trait Persisting[F[_]] {
  def persist[A](event: A)(handler: A ⇒ Unit): F[Unit]
}

object Persisting {
  def apply[F[_]](implicit F: Persisting[F]) = F

  def forActor(actor: PersistentActor): Persisting[Id] = new Persisting[Id] {
    override def persist[A](event: A)(handler: A => Unit): Unit = actor.persist(event)(handler)
  }
}