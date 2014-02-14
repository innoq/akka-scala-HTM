package service.task

import akka.actor.{ ActorLogging, ActorRef, Actor, Props }
import java.util.UUID

class TaskManager extends Actor with ActorLogging {

  var tasks = Map.empty[String, ActorRef]

  def receive = {
    case CreateTask(input, role, userId, delegateUser) => {
      log.debug("create task with data " + input)
      val task = this.context.actorOf(Task.props())
      val actorUUID = UUID.randomUUID()
      val taskId = actorUUID.toString
      tasks = tasks + (taskId -> task)
      task forward Init(taskId, input, role, userId, delegateUser)
    }
    case TaskCommand(taskId, command) => {
      log.debug(s"forward command $command to task $taskId")
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
