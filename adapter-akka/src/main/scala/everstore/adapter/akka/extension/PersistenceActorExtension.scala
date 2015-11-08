package everstore.adapter.akka.extension

import java.nio.file.Paths

import akka.actor._
import everstore.adapter.akka.PersistenceSnapshot
import everstore.adapter.akka.PersistenceSnapshotManager
import everstore.adapter.akka.kryo.KryoPersistenceSnapshotManager
import everstore.api.AdapterConfig
import everstore.api.JournalSize
import everstore.scala.ScalaAdapter
import everstore.scala.ScalaTransaction
import everstore.serialization.json4s.Json4sSerializer
import everstore.vanilla.VanillaDataStorageFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Failure
import scala.util.Success
import scala.util.control.NonFatal

class PersistenceActorExtensionImpl(config: AdapterConfig, snapshotManager: PersistenceSnapshotManager) extends Extension {
  val adapter = new ScalaAdapter(config)
  adapter.connect()

  def openTransaction(journalName: String) = adapter.openTransaction(journalName)

  def saveSnapshot[T](journalName: String, journalSize: JournalSize, state: T): Unit =
    snapshotManager.saveSnapshot[T](journalName, journalSize, state)

  def loadSnapshot[T](journalName: String): Option[PersistenceSnapshot[T]] =
    snapshotManager.loadSnapshot[T](journalName)

}

object PersistenceActorExtension
  extends ExtensionId[PersistenceActorExtensionImpl]
  with ExtensionIdProvider {

  override def createExtension(system: ExtendedActorSystem): PersistenceActorExtensionImpl = {
    val config = system.settings.config

    val adapterConfig = new AdapterConfig(
      config.getString("everstore.username"),
      config.getString("everstore.password"),
      config.getString("everstore.hostname"),
      6929,
      config.getInt("everstore.num-connections"),
      new Json4sSerializer,
      new VanillaDataStorageFactory)

    val path = Paths.get(config.getString("everstore.snapshot.directory"))
    val maxByteSize = try {
      config.getLong("everstore.snapshot.max-byte-size")
    } catch {
      case NonFatal(t) => 104857600L // 100 MB
    }
    val snapshotManager = new KryoPersistenceSnapshotManager(path, maxByteSize)

    new PersistenceActorExtensionImpl(adapterConfig, snapshotManager)
  }

  override def lookup(): ExtensionId[_ <: Extension] = PersistenceActorExtension

  override def get(system: ActorSystem): PersistenceActorExtensionImpl = super.get(system)
}

/**
 * Implement this trait to enable event sourcing
 */
trait PersistenceActor[T] {
  this: Actor with ActorLogging =>

  import PersistenceActor._

  type State = T

  private var _state: State = _
  private var _lastJournalOffset = JournalSize.ZERO
  private var _snapshotJournalOffset = JournalSize.ZERO
  private val extension = PersistenceActorExtension(context.system)

  /**
   * The name of this journal
   */
  val journalName: String

  /**
   * Retrieves the state managed by this actor. Send an UpdateState event to this actor to update it
   */
  def state = _state

  def openTransaction() = extension.openTransaction(journalName)

  def persist[E](evt: E)(implicit transaction: ScalaTransaction) = transaction.add(evt)

  def commit()(implicit transaction: ScalaTransaction) = transaction.commit()

  private def loadSnapshot(journalName: String) = extension.loadSnapshot[T](journalName)

  /**
   * Read events from an open transaction
   *
   * @param transaction The transaction
   * @return A promise to return a list of events occurring after the last read
   */
  private def readEventsFromOffset(transaction: ScalaTransaction) = transaction.readFromOffset(_lastJournalOffset)

  override final def receive: Receive = receiveCommand orElse defaultProcessCommand

  /**
   * Override this method to process custom commands
   *
   * @return
   */
  def receiveCommand: Receive

  /**
   * Check to see if we should save a snapshot. Only save a snapshot if enough
   * data has been changed in the journal.
   *
   * @param offset
   * @return
   */
  private def shouldWeSaveSnapshot(offset: JournalSize): Boolean =
    offset.sub(_snapshotJournalOffset) > 100

  /**
   * Process any persistence-specific internal commands
   *
   * @return
   */
  private def defaultProcessCommand: Receive = {
    case GetState =>
      val senderRef = sender()
      log.debug("Retrieving the states for this journal")
      openTransaction().foreach(transaction => {
        readEventsFromOffset(transaction).onComplete {
          case Success(data) =>
            val newState = processEvents(state, data)
            self ! UpdateState(newState, transaction.size)
            senderRef ! Option(newState)
          case Failure(NonFatal(t)) =>
            log.error("Could not read events", t)
            senderRef ! None
        }
      })
    case LoadSnapshot =>
      log.debug("Loading snapshot")
      loadSnapshot(journalName).foreach(snapshot => {
        if (snapshot.journalSize.isLargerThan(_lastJournalOffset)) {
          _state = snapshot.state
          _lastJournalOffset = snapshot.journalSize
        }
      })
    case SaveSnapshot(newState, offset) =>
      tryToSaveSnapshot(newState, offset)
    case UpdateState(newState, offset) =>
      log.debug("Trying to update the internal state")
      if (offset.isLargerThan(_lastJournalOffset)) {
        _state = newState.asInstanceOf[T]
        _lastJournalOffset = offset
        log.debug("Updated internal state")
        if (shouldWeSaveSnapshot(offset)) {
          self ! SaveSnapshot(newState, offset)
        }
      }
    case StopAndKillActor =>
      log.debug("Stopping persistence actor")
      context.stop(self)
      log.debug("Stopped persistence actor")
  }


  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    self ! LoadSnapshot
  }

  @throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    tryToSaveSnapshot(_state, _lastJournalOffset)
  }

  /**
   * Try to save a snapshot based on the supplied parameters
   *
   * @param newState
   * @param offset
   */
  private def tryToSaveSnapshot(newState: Any, offset: JournalSize): Unit = {
    log.debug("Saving snapshot")

    // Only save a snapshot if anything has been changed
    if (offset.isLargerThan(_snapshotJournalOffset)) {
      extension.saveSnapshot[T](journalName, offset, _state)
      _snapshotJournalOffset = offset
    }
  }

  /**
   * Process events loaded from Everstore. The first parameter is the state is it is right now.
   *
   *
   * @param state The current state
   * @param events A list containing all the new events added to the journal
   * @return The new state
   */
  private def processEvents(state: State, events: List[AnyRef]): State = {
    var newState = _state
    events.foreach(event => newState = processEvent(newState, event))
    newState
  } ensuring(_ != null, "The new state cannot be null")

  /**
   * Processes a state with a new event. Use this to modify the internal state managed by this actor
   *
   * {{{
   *  case class MyState(counter: Int)
   *  case class MyStateCreated()
   *  case class CounterIncremented(inc: Int)
   *
   *  class MyActor extends PersistenceActor[MyState] {
   *    override def processEvent(state: State, event: Any): State = event match {
   *      case MyStateCreated =>
   *        MyState(0)
   *      case CounterIncremented(inc) =>
   *        state.copy(counter = state.counter + inc)
   *    }
   *  }
   * }}}
   *
   *
   * @param state The state before we change it with the supplied event
   * @param event The event
   * @return A new version of the state
   */
  protected def processEvent(state: State, event: AnyRef): State
}

object PersistenceActor {

  sealed trait PersistenceEvent

  /**
   * Event responsible for updating the internal state for a specific actor.
   * The actor will, internally, make sure that the new state is newer than the one
   * already saved before it's going to update it
   *
   * @param newState The new state we want to set
   * @param offset The offset
   */
  case class UpdateState(newState: Any, offset: JournalSize) extends PersistenceEvent

  /**
   * Use this event to retrieve the state managed by this persistence actor as type Option[T].
   */
  case object GetState extends PersistenceEvent

  /**
   * Use this event to stop and kill an persistence actor in a thread-safe way
   */
  case object StopAndKillActor extends PersistenceEvent

  /**
   * Use this event to try to load a snapshot
   */
  case object LoadSnapshot extends PersistenceEvent

  /**
   * Event responsible for saving the supplied parameters as this actors snapshot
   *
   * @param newState
   * @param offset
   */
  case class SaveSnapshot(newState: Any, offset: JournalSize) extends PersistenceEvent

}