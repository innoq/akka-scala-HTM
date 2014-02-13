package service.task

import akka.actor.{ ActorRef, Actor, Props }
import java.util.UUID

class TaskManager extends Actor {

  var tasks = Map.empty[String, ActorRef]

  def receive = {
    case CreateTask(input) => {
      val task = this.context.actorOf(Task.props())
      val actorUUID = UUID.randomUUID()
      val taskId = actorUUID.toString
      tasks = tasks + (taskId -> this.sender)
      task forward Init(taskId, input)
    }
    case TaskCommand(taskId, command) => {
      tasks.get(taskId) match {
        case None => this.sender ! NoSuchTask(taskId)
        case Some(ref) => ref ! command
      }
    }
  }
}

object TaskManager {
  def props() = Props[TaskManager]
}
