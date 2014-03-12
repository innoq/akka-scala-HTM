package service.escalation

import akka.pattern.pipe
import akka.actor._
import service.core.ActorDefaults
import service.task._
import org.joda.time.{ Duration => DTDuration, DateTime }
import scala.concurrent.duration._
import play.api.libs.json.{ Json, JsValue }
import controllers.web.DomainSerializers
import play.api.libs.ws.WS
import akka.actor.FSM.CurrentState
import scala.Some
import service.task.ReClaim
import play.api.libs.json.JsObject
import akka.actor.FSM.SubscribeTransitionCallBack
import service.escalation.BaseEscalator.Protocol._

private[escalation] case class EscalationData(id: String, completionDeadline: DateTime, scheduledEscalation: Cancellable,
  role: Option[String], taskActor: ActorRef, state: TaskState)

abstract class BaseEscalator extends Actor with ActorLogging with ActorDefaults {

  import context.become

  def receive = {
    case InitEscalation(tId, tRole, esTaskActor, deadline) => {
      val cancel = scheduleCompletionWatch(tId, deadline)
      esTaskActor ! SubscribeTransitionCallBack(self)
      become(waitForTaskStateInit(EscalationData(tId, deadline, cancel, tRole, esTaskActor, Created)))
    }
  }

  def waitForTaskStateInit(data: EscalationData): Receive = {
    case CurrentState(self, state: TaskState) => {
      stayWithNewState(data, state)
    }
  }

  def stayWithNewState(data: EscalationData, state: TaskState) {
    become(updateTaskStateOrEscalate(data.copy(state = state)))
  }

  def updateTaskStateOrEscalate(data: EscalationData): Receive = transition(data) orElse escalate(data)

  def transition(data: EscalationData): Receive

  def escalate(data: EscalationData): Receive = {
    case Escalate(t) => {
      log.info(s"escalate task: ${data.id} in state ${data.state}")
      val json = Json.toJson(toTaskView(data))(DomainSerializers.taskViewWrites)
      callWithRetry(WS.url("http://localhost:3000/escalation").put(Json.obj("task" -> json)))
        .map { response =>
          log.info(response.body)
          buildReply(response.json)
        } pipeTo data.taskActor
    }
  }

  def scheduleCompletionWatch(id: String, completionDeadline: DateTime): Cancellable = {
    val minutes = timeToDeadline(completionDeadline)
    log.debug(s"task $id has to be be completed in $minutes, schedule escalation")
    context.system.scheduler.scheduleOnce(minutes, self, Escalate(1))
  }

  def timeToDeadline(completionDeadline: DateTime): FiniteDuration = {
    val timeToEscalation = new DTDuration(DateTime.now(), completionDeadline).getStandardMinutes
    timeToEscalation.minutes
  }

  def toTaskView(data: EscalationData): TaskView = {
    new TaskView(TaskModel(data.id, "generic", role = data.role, completionDeadline = Some(data.completionDeadline)), data.state)
  }

  def buildReply(value: JsValue) = {
    val taskDef = value.as[JsObject]
    val userId = taskDef \ "task" \ "user_id"
    userId.asOpt[Int] match {
      case None => log.warning("no handle for task escalation update defined: " + Json.stringify(value))
      case Some(user) => ReClaim(user.toString)
    }
  }
}

object BaseEscalator {
  object Protocol {
    sealed trait EscalatorCommands
    case class InitEscalation(id: String, role: Option[String], taskActor: ActorRef, deadline: DateTime) extends EscalatorCommands
    case class Escalate(a: Int) extends EscalatorCommands
    case object CheckForClaimOrEscalate extends EscalatorCommands
    case object StopEscalation extends EscalatorCommands
  }
}