package everstore.snapshot.events.kryo

import java.io.File
import java.io.IOException
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong

import com.twitter.chill.KryoPool
import com.twitter.chill.KryoSerializer
import com.twitter.chill.ScalaKryoInstantiator
import everstore.api.JournalSize
import everstore.api.snapshot.EventsSnapshotConfig
import everstore.api.snapshot.EventsSnapshotManager
import everstore.api.snapshot.EventsSnapshotEntry

/**
 * Represents a snapshot of a journal
 *
 * @param path The path to the journal
 * @param memorySize How many bytes the snapshot takes on the HDD
 * @param journalSize
 */
case class Snapshot(path: String, memorySize: Long, journalSize: JournalSize)

/**
 *
 * @param config
 */
class KryoSnapshotManager(config: EventsSnapshotConfig) extends EventsSnapshotManager {

  val bytesUsed: AtomicLong = new AtomicLong(0)
  val snapshots = new java.util.HashMap[String, Snapshot]()

  if (config.cleanOnInt && Files.exists(config.path)) {
    // Delete all files in the snapshot directory
    Files.walkFileTree(config.path, new SimpleFileVisitor[Path] {
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        Files.delete(file)
        FileVisitResult.CONTINUE
      }

      override def postVisitDirectory(file: Path, exc: IOException): FileVisitResult = {
        Files.delete(file)
        FileVisitResult.CONTINUE
      }
    })
  }

  // Create the snapshot directory is it doesn't exist
  if (!Files.exists(config.path)) {
    Files.createDirectory(config.path)
  }

  private val kryoPool = {
    val r = KryoSerializer.registerAll
    val ki = (new ScalaKryoInstantiator).withRegistrar(r)
    KryoPool.withByteArrayOutputStream(10, ki)
  }

  override def save(name: String, obj: EventsSnapshotEntry): Unit = {

    // Save a snapshot of the entry if it's missing
    snapshots synchronized {
      val snapshot = snapshots.get(name)
      if (snapshot == null) {
        snapshots.put(name, serialize(name, obj))
      } else if (snapshot.journalSize.isSmallerThen(obj.journalSize)) {
        snapshots.put(name, serialize(name, obj))
      }
    }

    // Remove entries from HDD if to much space is used
    while (bytesUsed.get() > config.maxBytes) {
      val firstEntry = snapshots.entrySet().iterator().next()
      val snapshot = firstEntry.getValue
      snapshots.remove(firstEntry.getKey)
      bytesUsed.addAndGet(-snapshot.memorySize)
      Files.deleteIfExists(Paths.get(snapshot.path))
    }
  }

  private def serialize[A](name: String, obj: EventsSnapshotEntry) = {
    // Create a path to the file if it doesn't exist
    val fileName = config.path + File.separator + name
    val fullPath = Paths.get(fileName)
    Files.createDirectories(fullPath.getParent)

    // Serialize it
    val binary = kryoPool.toBytesWithoutClass(obj)

    // Write the new snapshot
    Files.write(fullPath, binary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.DSYNC)

    // Increase the amount of memory used the snapshot manager
    bytesUsed.addAndGet(binary.length)

    Snapshot(fileName, binary.length, obj.journalSize)
  }

  override def load(name: String): Optional[EventsSnapshotEntry] = try {
    val fileName = config.path + File.separator + name
    val snapshot = Option(snapshots.get(name))
    val result = snapshot.map(s => {
      val binary = Files.readAllBytes(Paths.get(fileName))
      kryoPool.fromBytes(binary, classOf[EventsSnapshotEntry])
    })
    if (result.isDefined)
      Optional.of(result.get)
    else
      Optional.empty()
  } catch {
    case ex: IOException =>
      ex.printStackTrace()
      Optional.empty()
    case t: Throwable =>
      t.printStackTrace()
      Optional.empty()
  }

  override def memoryUsed: Long = bytesUsed.get()
}

