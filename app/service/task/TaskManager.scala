package service.task

import akka.actor._
import java.util.UUID
import service.task.Task.Protocol.TaskDone
import service.escalation.Escalator.Protocol.{ InitEscalation, StopEscalation }
import scala.Some
import service.escalation.Escalator
import org.joda.time.DateTime

private[task] case class TaskManageData(ref: ActorRef, escalator: Option[ActorRef])

class TaskManager extends Actor with ActorLogging {

  implicit val ec = context.dispatcher

  var tasks = Map.empty[String, TaskManageData]

  def receive = {
    case CreateTask(input, taskType, startDeadline, endDeadline, role, userId, delegateUser) => {
      log.debug("create task with data " + input)

      val taskActor = this.context.actorOf(Task.props())
      val taskId = UUID.randomUUID().toString
      taskActor forward Init(taskId, taskType, startDeadline, endDeadline, input, role, userId, delegateUser)

      tasks = tasks + (taskId -> TaskManageData(taskActor, escalation(taskId, role, startDeadline, endDeadline, taskActor)))
    }
    case TaskCommand(taskId, command) => {
      log.debug(s"forward command $command to task $taskId")
      tasks.get(taskId) match {
        case None => {
          log.info(s"task lookup ($taskId) failed")
          this.sender ! NoSuchTask(taskId)
        }
        case Some(TaskManageData(ref, _)) => ref forward command
      }
    }
    case TaskDone(taskId, state, _) => {
      tasks.get(taskId) match {
        case None => {
          log.error(s"task done handling of task $taskId failed (task not managed by task manager)")
        }
        case Some(TaskManageData(ref, Some(escalation))) => {
          log.debug(s"cancel deadline scheduler for task $taskId")
          escalation ! StopEscalation
        }
        case _ => ""
      }
    }
  }

  def escalation(taskId: String, role: Option[String], startDeadline: Option[DateTime], endDeadline: Option[DateTime], taskActor: ActorRef) = {
    val esc = startDeadline.orElse(endDeadline).map(e => context.actorOf(Escalator.props))
    esc.foreach(_ ! InitEscalation(taskId, role, taskActor, startDeadline, endDeadline))
    esc
  }
}

object TaskManager {

  val actorName = "TaskManager"

  def actorPath = s"/user/$actorName"

  def props = Props[TaskManager]
}
