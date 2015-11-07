package everstore.adapter.akka

import com.typesafe.config.ConfigFactory

import scala.util.control.NonFatal

object PersistenceModule {
  val config = ConfigFactory.load()

  /**
   * The directory where we want to store our snapshots
   */
  lazy val snapshotDirectory = try {
    Some(config.getString("everstore.snapshot.directory"))
  } catch {
    case NonFatal(e) => None
  }

  /**
   * The class driving the snapshot loading- and saving
   */
  lazy val snapshotManagerClass = try {
    val className = config.getString("everstore.snapshot.manager-class")
    Some(Class.forName(className).newInstance.asInstanceOf[ActorSnapshotManager])
  } catch {
    case NonFatal(e) => None
  }
}
