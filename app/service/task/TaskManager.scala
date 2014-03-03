package service.task

import akka.actor._
import java.util.UUID
import org.joda.time.{ Duration => DTDuration, DateTime }
import scala.concurrent.duration._
import service.task.Task.Protocol.TaskDone
import service.escalation.EscalationService.Protocol.Escalate
import scala.Some

private[task] case class TaskManageData(ref: ActorRef, completionDeadline: Option[Cancellable])

class TaskManager(val escalationService: ActorRef) extends Actor with ActorLogging {

  implicit val ec = context.dispatcher

  var tasks = Map.empty[String, TaskManageData]

  def receive = {
    case CreateTask(input, taskType, startDeadline, endDeadline, role, userId, delegateUser) => {
      log.debug("create task with data " + input)

      val taskActor = this.context.actorOf(Task.props())
      val taskId = UUID.randomUUID().toString
      taskActor forward Init(taskId, taskType, startDeadline, endDeadline, input, role, userId, delegateUser)

      val cancel = startDeadline.map(dl => scheduleDeadlineWatch(taskId, taskActor, dl))

      tasks = tasks + (taskId -> TaskManageData(taskActor, cancel))
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
        case Some(TaskManageData(ref, Some(compDeadline))) => {
          log.debug(s"cancel deadline scheduler for task $taskId")
          compDeadline.cancel()
        }
        case _ => ""
      }
    }
  }

  def scheduleDeadlineWatch(id: String, taskActor: ActorRef, completionDeadline: DateTime): Cancellable = {
    val timeToEscalation = new DTDuration(DateTime.now(), completionDeadline).getStandardMinutes
    val minutes = timeToEscalation.minutes
    log.debug(s"task $id has to be be completed in $minutes, schedule escalation")
    context.system.scheduler.scheduleOnce(minutes, escalationService, Escalate(id, taskActor))
  }
}

object TaskManager {

  val actorName = "TaskManager"

  def actorPath = s"/user/$actorName"

  def props(escalationService: ActorRef) = Props(classOf[TaskManager], escalationService)
}
