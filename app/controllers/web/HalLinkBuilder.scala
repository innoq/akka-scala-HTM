package controllers.web

import service.task._
import controllers.routes

object HalLinkBuilder {

  def self(id: String) = "self" -> routes.TaskView.lookup(id)

  def claim(id: String) = "claim" -> routes.Tasks.claim(id)

  def skip(id: String) = "skip" -> routes.Tasks.skip(id)

  def start(id: String) = "start" -> routes.Tasks.start(id)

  def release(id: String) = "release" -> routes.Tasks.release(id)

  def complete(id: String) = "complete" -> routes.Tasks.complete(id)

  def stop(id: String) = "stop" -> routes.Tasks.stop(id)

  def links(task: TaskView) = {
    val id = task.id
    task.taskState match {
      case Created => Vector.empty
      case Ready => Vector(self(id), skip(id), claim(id))
      case Reserved => Vector(self(id), skip(id), start(id), release(id))
      case InProgress => Vector(self(id), skip(id), complete(id), stop(id))
      case Obsolete => Vector(self(id))
      case Completed => Vector(self(id))
    }
  }

}
