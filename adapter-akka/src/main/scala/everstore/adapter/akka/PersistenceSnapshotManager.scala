package everstore.adapter.akka

import everstore.api.JournalSize

case class PersistenceSnapshot[T](journalSize: JournalSize, state: T)

trait PersistenceSnapshotManager {

  def saveSnapshot[T](journalName: String, journalSize: JournalSize, state: T): Unit

  def loadSnapshot[T](journalName: String): Option[PersistenceSnapshot[T]]
}
