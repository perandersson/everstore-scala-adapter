package everstore.scala


import java.lang
import java.util.concurrent.CompletableFuture
import java.util.function.BiFunction
import java.util.{List => JList}

import everstore.api.CommitResult
import everstore.api.JournalSize
import everstore.api.Transaction

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.Promise

class ScalaTransaction(transaction: Transaction) {
  def size = transaction.size()

  def readFromOffset(offset: JournalSize): Future[List[AnyRef]] = {
    val result = transaction.readFromOffset(offset)
    val promise = Promise[List[AnyRef]]()
    result.handle(new BiFunction[JList[AnyRef], Throwable, JList[AnyRef]]() {
      override def apply(t: JList[AnyRef], throwable: Throwable): JList[AnyRef] = {
        if (throwable == null) {
          promise.success(t.toList)
        } else {
          promise.failure(throwable)
        }
        null
      }
    })
    promise.future
  }

  def rollback(): Future[Boolean] = {
    val result = transaction.rollback()
    val promise = Promise[Boolean]()
    result.handle(new BiFunction[lang.Boolean, Throwable, lang.Boolean]() {
      override def apply(t: lang.Boolean, success: Throwable): lang.Boolean = {
        if (success == null) {
          promise.success(t)
        } else {
          promise.failure(success)
        }
        null
      }
    })
    promise.future
  }

  def read(): Future[List[AnyRef]] = readFromOffset(JournalSize.ZERO)

  def add[T](event: T) = transaction.add(event)

  def commit(): Future[CommitResult] = {
    val result = transaction.commit()
    val promise = Promise[CommitResult]()
    result.handle(new BiFunction[CommitResult, Throwable, CommitResult]() {
      override def apply(t: CommitResult, success: Throwable): CommitResult = {
        if (success == null) {
          promise.success(t)
        } else {
          promise.failure(success)
        }
        null
      }
    })
    promise.future
  }
}
