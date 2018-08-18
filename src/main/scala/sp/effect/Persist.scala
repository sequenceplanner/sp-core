package sp.effect

import akka.persistence.PersistentActor
import sp.effect.Kinds.Id

trait Persist[F[_]] {
  def persist[A](event: A)(handler: A ⇒ Unit): F[Unit]
}

object Persist {
  def apply[F[_]](implicit F: Persist[F]) = F

  def forActor(actor: PersistentActor): Persist[Id] = new Persist[Id] {
    override def persist[A](event: A)(handler: A => Unit): Unit = actor.persist(event)(handler)

  }
}