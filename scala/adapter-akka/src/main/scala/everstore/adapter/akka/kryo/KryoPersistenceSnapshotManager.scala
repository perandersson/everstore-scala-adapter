package everstore.adapter.akka.kryo

import java.io.IOException
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.atomic.AtomicLong

import com.twitter.chill.KryoPool
import com.twitter.chill.KryoSerializer
import com.twitter.chill.ScalaKryoInstantiator
import everstore.adapter.akka.PersistenceSnapshot
import everstore.adapter.akka.PersistenceSnapshotManager
import everstore.api.JournalSize
import everstore.api.Offset

import scala.util.control.NonFatal

case class KryoSnapshotEntry(path: Path, memorySize: Long)

case class KryoSnapshot(journalSize: JournalSize, state: Any)

class KryoPersistenceSnapshotManager(rootPath: Path, maxByteSize: Long) extends PersistenceSnapshotManager {

  private val bytesUsed: AtomicLong = new AtomicLong(0)
  private val snapshots = new java.util.HashMap[String, KryoSnapshotEntry]()

  // Ensure that the directory exists
  if (!Files.exists(rootPath)) {
    Files.createDirectory(rootPath)
  }

  // Iterate over all files and cache metadata of them
  Files.walkFileTree(rootPath, new SimpleFileVisitor[Path] {
    override def visitFile(file: Path, attr: BasicFileAttributes): FileVisitResult = {
      if (attr.isRegularFile) {
        val snapshotFilename = file.toString
        val snapshotFileSize = Files.size(file)
        snapshots.put(snapshotFilename, KryoSnapshotEntry(file, snapshotFileSize))
        bytesUsed.addAndGet(snapshotFileSize)
      }

      FileVisitResult.CONTINUE
    }

    override def postVisitDirectory(file: Path, exc: IOException): FileVisitResult = {
      FileVisitResult.CONTINUE
    }
  })

  private val kryoPool = {
    val r = KryoSerializer.registerAll
    val ki = (new ScalaKryoInstantiator).withRegistrar(r)
    KryoPool.withByteArrayOutputStream(10, ki)
  }

  override def saveSnapshot[T](journalName: String, journalSize: JournalSize, state: T): Unit = {
    require(journalName.length > 0, "The journal name is not valid")
    require(state != null, "The supplied state cannot be null")

    val snapshotPath = Paths.get(rootPath.toString, journalName)

    snapshots synchronized {
      val snapshotFilename = snapshotPath.toString
      val potentialSnapshot = Option(snapshots.get(snapshotFilename))
      val oldSize = potentialSnapshot.map(_.memorySize).getOrElse(0L)
      val entry = serialize(snapshotPath, journalSize, state)
      val deltaSize = entry.memorySize - oldSize

      // Save the snapshot information in memory
      snapshots.put(snapshotFilename, entry)

      // Update how many bytes used
      bytesUsed.addAndGet(deltaSize)
    }
  }

  private def serialize[T](path: Path, journalSize: JournalSize, state: T) = {
    // Create a path to the file if it doesn't exist
    Files.createDirectories(path.getParent)

    // Serialize it
    val binary = kryoPool.toBytesWithoutClass(KryoSnapshot(journalSize, state))

    // Write the new snapshot
    Files.write(path, binary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
      StandardOpenOption.DSYNC)

    // Increase the amount of memory used the snapshot manager
    bytesUsed.addAndGet(binary.length)

    KryoSnapshotEntry(path, binary.length)
  }

  override def loadSnapshot[T](journalName: String): Option[PersistenceSnapshot[T]] = {
    require(journalName.length > 0, "The journal name is not valid")

    try {
      val path = Paths.get(rootPath.toString, journalName)
      val fileName = path.toString
      val potentialSnapshot = Option(snapshots.get(fileName))
      potentialSnapshot.map(entry => {
        val binary = Files.readAllBytes(entry.path)
        val instance = kryoPool.fromBytes(binary, classOf[KryoSnapshot])

        instance
      }).flatMap(snapshot => {
        Option(snapshot.state)
          .map(_.asInstanceOf[T])
          .map(i => PersistenceSnapshot[T](snapshot.journalSize, snapshot.state.asInstanceOf[T]))
      })
    } catch {
      case NonFatal(t) =>
        t.printStackTrace()
        None
    }
  } ensuring (_ != null)
}
