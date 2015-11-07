package examples.console

import everstore.api.AdapterConfig
import everstore.scala.ScalaAdapter
import everstore.serialization.json4s.Json4sSerializer
import everstore.vanilla.VanillaDataStorageFactory

import scala.async.Async._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

object Example2 extends App {

  // Adapter configuration
  val configuration = new AdapterConfig("admin", "passwd", "localhost",
    6929, 6, new Json4sSerializer, new VanillaDataStorageFactory)

  // Create and initialize the adapter
  val adapter = new ScalaAdapter(configuration)
  adapter.connect()

  def test() = {
    val millis1 = System.currentTimeMillis()
    val lengths = List.range(0, 100).map(i => {
      async {
        val journal = "/org/org_" + ((i % 10) + 1)
        val t = await {
          adapter.openTransaction(journal)
        }
        val events = await {
          t.read()
        }
        t.rollback()
        events.size
      }
    })

    val sum = Future.sequence(lengths).map(_.sum)
    val s = Await.result(sum, 1 minutes)
    val millis2 = System.currentTimeMillis()
    print(s)
    print(" --> ")
    println(millis2 - millis1)
  }

  test()
  test()
  test()
  test()
  test()
  test()
  test()
  test()
  test()
  test()
  test()
  test()
  test()
  test()
  test()
  test()
  test()
  test()
  test()
  test()


  adapter.close()
}
