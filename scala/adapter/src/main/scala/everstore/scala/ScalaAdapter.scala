package everstore.scala

import java.util.concurrent.CompletableFuture
import java.util.function.BiFunction

import everstore.api.Adapter
import everstore.api.AdapterConfig
import everstore.api.Transaction

import scala.concurrent.Promise

class ScalaAdapter(config: AdapterConfig) {
  val adapter = new Adapter(config)

  def connect() = adapter.connect()

  def close() = adapter.close()

  def openTransaction(name: String) = {
    val transaction = adapter.openTransaction(name).asInstanceOf[CompletableFuture[Transaction]]
    val promise = Promise[ScalaTransaction]()
    transaction.handle(new BiFunction[Transaction, Throwable, Transaction]() {
      override def apply(t: Transaction, throwable: Throwable): Transaction = {
        if (throwable == null) {
          promise.success(new ScalaTransaction(t))
        } else {
          promise.failure(throwable)
        }
        null
      }
    })

    promise.future
  }
}
