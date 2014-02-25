package service.task

import akka.actor.{ ActorLogging, ActorRef, Actor, Props }
import java.util.UUID
import org.joda.time.{ Duration => DTDuration, DateTime }
import scala.concurrent.duration._
import service.escalation.EscalationService.Protocol.Escalate

class TaskManager(val escalationService: ActorRef) extends Actor with ActorLogging {

  implicit val ec = context.dispatcher

  var tasks = Map.empty[String, ActorRef]

  def receive = {
    case CreateTask(input, taskType, startDeadline, endDeadline, role, userId, delegateUser) => {
      log.debug("create task with data " + input)

      val taskActor = this.context.actorOf(Task.props())
      val taskId = UUID.randomUUID().toString
      tasks = tasks + (taskId -> taskActor)
      taskActor forward Init(taskId, taskType, startDeadline, endDeadline, input, role, userId, delegateUser)

      startDeadline.foreach(dl => scheduleDeadlineWatch(taskId, dl))
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

  def scheduleDeadlineWatch(id: String, completionDeadline: DateTime) {
    val timeToEscalation = new DTDuration(DateTime.now(), completionDeadline).getStandardMinutes
    val minutes = timeToEscalation.minutes
    log.debug(s"task $id has to be be completed in $minutes, schedule escalation")
    context.system.scheduler.scheduleOnce(minutes, escalationService, Escalate(id))
  }
}

object TaskManager {

  val actorName = "TaskManager"

  def actorPath = s"/user/$actorName"

  def props(escalationService: ActorRef) = Props(classOf[TaskManager], escalationService)
}
