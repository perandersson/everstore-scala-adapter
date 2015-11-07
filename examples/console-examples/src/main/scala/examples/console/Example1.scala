package examples.console

import everstore.api.AdapterConfig
import everstore.scala.ScalaAdapter
import everstore.serialization.json4s.Json4sSerializer
import everstore.vanilla.VanillaDataStorageFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Failure
import scala.util.Success

/**
 * Example where we open a journal and save an event to it
 */
object Example1 extends App {

  // Adapter configuration
  val configuration = new AdapterConfig("admin", "passwd", "localhost",
    6929, 6, new Json4sSerializer, new VanillaDataStorageFactory)

  // Create and initialize the adapter
  val adapter = new ScalaAdapter(configuration)
  adapter.connect()

  // Some case class
  trait UserEvent

  case class UserCreated(username: String) extends UserEvent

  // Open two competing transaction
  val journal = adapter.openTransaction("/user/per.andersson@funnic.com")
  val journal2 = adapter.openTransaction("/user/per.andersson@funnic.com")

  journal.onComplete({
    case Success(j) =>
      j.add(UserCreated("per.andersson@funnic.com"))
      j.commit().map({
        case commit if commit.success =>
          println("Commit successfull")
        case commit if !commit.success =>
          println(s"Could not commit the following events to the journal:\n ${commit.events}")
      })
    case Failure(f) =>
      f.printStackTrace()
  })

  journal2.onComplete({
    case Success(j) =>
      j.add(UserCreated("dim.ravne@gmail.com"))
      j.commit().map({
        case commit if commit.success =>
          println("Commit successfull")
        case commit if !commit.success =>
          println(s"Could not commit the following events to the journal:\n ${commit.events}")
      })
    case Failure(f) =>
      f.printStackTrace()
  })

  Thread.sleep(10000)
  adapter.close()
}
