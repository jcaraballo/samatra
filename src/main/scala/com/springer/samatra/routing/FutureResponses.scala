package com.springer.samatra.routing

import com.springer.samatra.routing.AsyncResponses.{Async, AsyncCancellation, AsyncHttpResp}
import com.springer.samatra.routing.Routings.HttpResp

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import scala.util.Try

object FutureResponses {

  val defaultTimeout = 15000

  object Implicits {
    implicit def fromFuture[T](fut: Future[T])(implicit rest: T => HttpResp, executor: ExecutionContext): HttpResp = FutureResponses.fromFuture(fut, logThreadDumpOnTimeout = false)
  }

  class NoCancelForScalaFutures extends AsyncCancellation {
    override def cancel(): Unit = () //no-op - no way to cancel scala futures
  }

  class FutureBackedAsync[T](f: Future[T])(implicit ex: ExecutionContext) extends Async[T] {
    override def onComplete(t: (Try[T]) => Unit): AsyncCancellation = {
      f.onComplete(t)
      new NoCancelForScalaFutures()
    }
  }

  def fromFuture[T](fut: Future[T], logThreadDumpOnTimeout: Boolean)(implicit rest: T => HttpResp, executor: ExecutionContext): HttpResp =
    AsyncHttpResp(new FutureBackedAsync(fut), defaultTimeout, rest, logThreadDumpOnTimeout)

  def fromFutureWithTimeout[T](timeout: Long, fut: Future[T], logThreadDumpOnTimeout: Boolean = false)(implicit rest: T => HttpResp, executor: ExecutionContext): HttpResp =
    AsyncHttpResp(new FutureBackedAsync(fut), timeout, rest, logThreadDumpOnTimeout)
}
