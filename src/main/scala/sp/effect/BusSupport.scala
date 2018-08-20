package sp.effect

import akka.actor.Actor
import sp.service.MessageBussSupport

/**
  * Provides an adapter for MessageBusSupport. Usage is only necessary when an ActorContext
  * or an ActorRef is not immediately available at the declaration site.
  */
trait BusSupport {
  private var busSupport: Option[MessageBussSupport] = None
  def busCreated: Boolean = busSupport.nonEmpty

  /**
    * A list of topics that the bus will subscribe to.
    */
  def busSubscriptionTopics: Seq[String]

  protected def initBus(handler: Actor, subscriptionTopics: String*): Unit = {
    busSupport = Some(new MessageBussSupport {
      override val context = handler.context
      override val self = handler.self
    })

    busSupport.foreach { bus =>
      bus.init
      busSubscriptionTopics.foreach(bus.subscribe)
    }

  }

  def publishOnBus(topic: String, json: String): Unit = busSupport.foreach(_.publish(topic, json))
}