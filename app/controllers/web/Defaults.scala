package controllers.web

import scala.concurrent.duration._
import akka.util.Timeout

trait Defaults {

  implicit val defaultTimeout = Timeout(2.seconds)

}
