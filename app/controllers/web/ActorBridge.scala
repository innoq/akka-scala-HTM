package controllers.web

import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global
import akka.actor.ActorSelection
import play.api.mvc.SimpleResult
import play.api.libs.concurrent.Akka
import service.task.{ TaskListReadModelActor, TaskManager }
import akka.pattern.ask
import play.api.Play.current

trait ActorBridge extends Defaults {

  def askDefault(ref: ActorSelection, msg: AnyRef)(handle: Any => SimpleResult) = {
    ask(ref, msg).map(handle)
  }

  def taskManagerActor = Akka.system.actorSelection(TaskManager.actorPath)

  def readModelActor = Akka.system.actorSelection(TaskListReadModelActor.actorPath)
}
