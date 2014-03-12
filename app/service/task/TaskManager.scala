package service.task

import akka.actor._
import java.util.UUID
import service.task.Task.Protocol.TaskDone
import scala.Some
import org.joda.time.DateTime
import service.escalation.BaseEscalator.Protocol.InitEscalation
import service.escalation.{ StartDeadlineEscalator, CompletionDeadlineEscalator }

class TaskManager extends Actor with ActorLogging {

  implicit val ec = context.dispatcher

  var tasks = Map.empty[String, ActorRef]

  def receive = {
    case CreateTask(input, taskType, startDeadline, endDeadline, role, userId, delegateUser) => {
      log.debug("create task with data " + input)

      val taskActor = this.context.actorOf(Task.props())
      val taskId = UUID.randomUUID().toString
      taskActor forward Init(taskId, taskType, startDeadline, endDeadline, input, role, userId, delegateUser)

      scheduleEscalations(taskId, role, startDeadline, endDeadline, taskActor)
      tasks = tasks + (taskId -> taskActor)
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
    //    case TaskDone(taskId, state, _) => {
    //      tasks.get(taskId) match {
    //        case None => {
    //          log.error(s"task done handling of task $taskId failed (task not managed by task manager)")
    //        }
    //      }
    //    }
  }

  def scheduleEscalations(taskId: String, role: Option[String], startDeadline: Option[DateTime], endDeadline: Option[DateTime], taskActor: ActorRef) {
    def planEscalation(deadline: Option[DateTime], props: Props) = {
      deadline.map(dl => dl -> context.actorOf(props))
        .foreach { case (dl, actor) => actor ! InitEscalation(taskId, role, taskActor, dl) }
    }
    planEscalation(startDeadline, StartDeadlineEscalator.props)
    planEscalation(endDeadline, CompletionDeadlineEscalator.props)
  }

}

object TaskManager {

  val actorName = "TaskManager"

  def actorPath = s"/user/$actorName"

  def props = Props[TaskManager]
}
