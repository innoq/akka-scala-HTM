package service.core

import akka.actor.Actor
import akka.pattern.CircuitBreaker
import concurrent.duration._
import play.api.libs.ws.{ WS, Response }
import scala.concurrent.Future

trait ActorDefaults { self: Actor =>

  implicit val ec = context.dispatcher

  val breaker =
    new CircuitBreaker(context.system.scheduler,
      maxFailures = 5,
      callTimeout = 10.seconds,
      resetTimeout = 1.minute) onOpen notifyOnOpen

  protected def notifyOnOpen() = {
    //
  }

  def callWithRetry(call: => Future[Response]): Future[Response] = {
    breaker withCircuitBreaker {
      implicit val success = new retry.Success[Response](_.status == 200)
      retry.Directly(3) { () => call }
    }
  }
}
