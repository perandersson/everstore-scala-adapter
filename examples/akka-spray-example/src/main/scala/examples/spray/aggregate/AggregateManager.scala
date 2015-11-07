package examples.spray.aggregate

import akka.actor.ActorContext
import akka.actor.ActorRef
import akka.actor.Props
import everstore.adapter.akka.extension.PersistenceActor.StopAndKillActor

object AggregateManager {
  // How many children this aggregate manager allows to exist at once
  val maxChildren = 5000

  // How many children to be destroyed in one "kill" procedure
  val childrenToKillAtOnce = 20
}

trait AggregateManager {
  import AggregateManager._

  val context: ActorContext

  private var childrenBeingTerminated: Set[ActorRef] = Set.empty

  /**
   * Retrieves the actor responsible for the supplied unique ID
   *
   * @param id
   * @return
   */
  def findOrCreate(id: String) =
    context.child(id) getOrElse create(id)

  /**
   * Create a new actor and watch it to ensure that it's not GC-ed and also so that
   * we get an event when the actor is destroyed
   *
   * @param id
   * @return
   */
  private def create(id: String) = {
    killChildrenIfNecessary()
    val agg = context.actorOf(aggregateProps(id), id)
    context watch agg
    agg
  }

  /**
   * Kill children if we've reached the maximum number of concurrent in-memory
   * aggregates
   */
  private def killChildrenIfNecessary() = {
    val numAggregates = context.children.size - childrenBeingTerminated.size
    if (numAggregates > maxChildren) {
      val childrenNotBeingTerminated = context.children.filterNot(childrenBeingTerminated)
      val childrenToKill = childrenNotBeingTerminated take childrenToKillAtOnce
      childrenToKill foreach (_ ! StopAndKillActor)
      childrenBeingTerminated ++= childrenToKill
    }
  }

  /**
   * Returns Props used to create an aggregate with specified id
   *
   * @param id Aggregate id
   * @return Props to create aggregate
   */
  def aggregateProps(id: String): Props
}
