package sp

import akka.actor.ActorSystem

class AkkaCluster(system: ActorSystem) {
  val cluster = akka.cluster.Cluster(system)

  cluster.registerOnMemberUp(println(Text.NodeJoinedCluster))
  cluster.registerOnMemberRemoved(println(Text.NodeLeftCluster))
}
