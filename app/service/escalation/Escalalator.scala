package service.escalation

import akka.actor._
import akka.pattern.pipe
import service.escalation.Escalalator.Protocol._
import service.core.ActorDefaults
import play.api.libs.ws.WS
import play.api.libs.json.{ JsValue, Json }
import controllers.web.DomainSerializers
import service.task._
import service.escalation.Escalalator.Protocol.Escalate
import org.joda.time.{ Duration => DTDuration, DateTime }
import scala.concurrent.duration._
import akka.actor.FSM.Transition
import service.escalation.Escalalator.Protocol.InitEscalation
import akka.actor.FSM.CurrentState
import scala.Some
import service.task.ReClaim
import play.api.libs.json.JsObject
import akka.actor.FSM.SubscribeTransitionCallBack

class Escalalator extends Actor with ActorLogging with ActorDefaults {

  var id: String = _
  var cState: TaskState = _
  var taskDeadline: DateTime = _
  var role: Option[String] = None
  var taskActor: ActorRef = _
  var cancelation: Cancellable = _

  def receive = {
    case InitEscalation(tId, tRole, esTaskActor, completionDeadline) => {
      log.info(s"init esclation $tId")
      id = tId
      role = tRole
      taskDeadline = completionDeadline
      taskActor = esTaskActor
      taskActor ! SubscribeTransitionCallBack(self)
      cancelation = scheduleDeadlineWatch(completionDeadline)
    }
    case CurrentState(self, state: TaskState) => {
      log.info(s"escalation state update $id to $state")
      cState = state
    }
    case Transition(actorRef, oldState, newState: TaskState) => {
      log.info(s"escalation state update $id to $newState")
      cState = newState
    }
    case Escalate => {
      log.info("escalate task: " + id)
      val json = Json.toJson(
        new TaskView(TaskModel(id, "generic", role = role, completionDeadline = Some(taskDeadline)), cState))(DomainSerializers.taskViewWrites)
      callWithRetry(WS.url("http://localhost:3000/escalation").put(Json.obj("task" -> json)))
        .map { response =>
          log.info(response.body)
          buildReply(response.json)
        } pipeTo taskActor
    }
    case StopEscalation => {
      log.info(s"stop escalation for task $id")
      cancelation.cancel()
    }
  }

  def buildReply(value: JsValue) = {
    val taskDef = value.as[JsObject]
    val userId = taskDef \ "task" \ "user_id"
    userId.asOpt[Int] match {
      case None => log.warning("no handle for task escalation update defined: " + Json.stringify(value))
      case Some(user) => ReClaim(user.toString)
    }
  }

  def scheduleDeadlineWatch(completionDeadline: DateTime): Cancellable = {
    val timeToEscalation = new DTDuration(DateTime.now(), completionDeadline).getStandardMinutes
    val minutes = timeToEscalation.minutes
    log.debug(s"task $id has to be be completed in $minutes, schedule escalation")
    context.system.scheduler.scheduleOnce(minutes, self, Escalate)
  }
}

object Escalalator {
  val actorName = "Escalator"

  def actorPath = s"/user/$actorName"

  def props: Props = Props[Escalalator]

  object Protocol {
    sealed trait EscalatorCommands
    case class InitEscalation(id: String, role: Option[String], taskActor: ActorRef, completionDeadline: DateTime) extends EscalatorCommands
    case object Escalate extends EscalatorCommands
    case object StopEscalation
  }
}
