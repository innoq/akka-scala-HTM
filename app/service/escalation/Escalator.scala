package service.escalation

import akka.actor._
import akka.pattern.pipe
import service.escalation.Escalator.Protocol._
import service.core.ActorDefaults
import play.api.libs.ws.WS
import play.api.libs.json.{ JsValue, Json }
import controllers.web.DomainSerializers
import service.task._
import service.escalation.Escalator.Protocol.Escalate
import org.joda.time.{ Duration => DTDuration, DateTime }
import scala.concurrent.duration._
import akka.actor.FSM.Transition
import service.escalation.Escalator.Protocol.InitEscalation
import akka.actor.FSM.CurrentState
import scala.Some
import service.task.ReClaim
import play.api.libs.json.JsObject
import akka.actor.FSM.SubscribeTransitionCallBack

case class EscalationData(id: String, completionDeadline: DateTime, cancel: Cancellable,
  role: Option[String], taskActor: ActorRef, state: TaskState)

class Escalator extends Actor with ActorLogging with ActorDefaults {

  def receive = {
    case InitEscalation(tId, tRole, esTaskActor, tStartDeadline, tCompletionDeadline) => {
      log.info(s"init esclation $tId")
      val dl = tCompletionDeadline.get
      val cancel = scheduleCompletionWatch(tId, dl)
      esTaskActor ! SubscribeTransitionCallBack(self)
      context.become(waitForTaskStateInit(EscalationData(tId, dl, cancel, tRole, esTaskActor, Created)))
    }
  }

  def waitForTaskStateInit(data: EscalationData): Receive = {
    case CurrentState(self, state: TaskState) => {
      log.info(s"escalation state update ${data.id} to $state")
      context.become(updateTaskStateOrEscalate(data.copy(state = state)))
    }
  }

  def updateTaskStateOrEscalate(data: EscalationData): Receive = {
    case Transition(actorRef, oldState, newState: TaskState) => {
      log.info(s"escalation state update ${data.id} to $newState")
      context.become(updateTaskStateOrEscalate(data.copy(state = newState)))
    }
    case Escalate(t) => {
      log.info("escalate task: " + data.id)
      val json = Json.toJson(
        new TaskView(TaskModel(data.id, "generic", role = data.role, completionDeadline = Some(data.completionDeadline)), data.state))(DomainSerializers.taskViewWrites)
      callWithRetry(WS.url("http://localhost:3000/escalation").put(Json.obj("task" -> json)))
        .map { response =>
          log.info(response.body)
          buildReply(response.json)
        } pipeTo data.taskActor
    }

    //    case CheckForClaimOrEscalate => {
    //      log.info(s"check if task $id gets escalated")
    //      state match {
    //        case Reserved | InProgress => log.info("escalation halted")
    //        case _ => self ! Escalate
    //      }
    //    }

    case StopEscalation => {
      log.info(s"stop escalation for task ${data.id}")
      //startDeadline.foreach(_._2.cancel())
      data.cancel.cancel()
    }

    case e => log.info("whattts")
  }

  def buildReply(value: JsValue) = {
    val taskDef = value.as[JsObject]
    val userId = taskDef \ "task" \ "user_id"
    userId.asOpt[Int] match {
      case None => log.warning("no handle for task escalation update defined: " + Json.stringify(value))
      case Some(user) => ReClaim(user.toString)
    }
  }

  def scheduleCompletionWatch(id: String, completionDeadline: DateTime): Cancellable = {
    val minutes = timeToDeadline(completionDeadline)
    log.debug(s"task $id has to be be completed in $minutes, schedule escalation")
    context.system.scheduler.scheduleOnce(minutes, self, Escalate(1))
  }

  //  def scheduleStartWatch(startDeadline: DateTime): Cancellable = {
  //    val minutes = timeToDeadline(startDeadline)
  //    log.debug(s"task $id has to be be started in $minutes, schedule escalation")
  //    context.system.scheduler.scheduleOnce(minutes, self, Escalate)
  //  }

  def timeToDeadline(completionDeadline: DateTime): FiniteDuration = {
    val timeToEscalation = new DTDuration(DateTime.now(), completionDeadline).getStandardMinutes
    timeToEscalation.minutes
  }
}

object Escalator {
  val actorName = "Escalator"

  def actorPath = s"/user/$actorName"

  def props: Props = Props[Escalator]

  object Protocol {
    sealed trait EscalatorCommands
    case class InitEscalation(id: String, role: Option[String], taskActor: ActorRef, startDeadline: Option[DateTime], completionDeadline: Option[DateTime]) extends EscalatorCommands
    case class Escalate(a: Int) extends EscalatorCommands
    case object CheckForClaimOrEscalate extends EscalatorCommands
    case object StopEscalation extends EscalatorCommands
  }
}
