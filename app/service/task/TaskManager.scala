package service.task

import akka.actor.{ ActorLogging, ActorRef, Actor, Props }
import java.util.UUID

class TaskManager extends Actor with ActorLogging {

  var tasks = Map.empty[String, ActorRef]

  def receive = {
    case CreateTask(input, taskType, startDeadline, endDeadline, role, userId, delegateUser) => {
      log.debug("create task with data " + input)
      val task = this.context.actorOf(Task.props())
      val actorUUID = UUID.randomUUID()
      val taskId = actorUUID.toString
      //context.system.scheduler.scheduleOnce()
      tasks = tasks + (taskId -> task)
      task forward Init(taskId, taskType, startDeadline, endDeadline, input, role, userId, delegateUser)
    }
    case TaskCommand(taskId, command) => {
      log.debug(s"forward command $command to task $taskId")
      tasks.get(taskId) match {
        case None => {
          log.info(s"task lookup ($taskId) failed")
          this.sender ! NoSuchTask(taskId)
        }
        case Some(ref) => ref forward command
      }
    }
  }
}

object TaskManager {

  val actorName = "TaskManager"

  def actorPath = s"/user/$actorName"

  def props() = Props[TaskManager]
}
