package service.escalation

import akka.actor.{ ActorRef, ActorLogging, Props, Actor }
import akka.pattern.{ pipe, ask }
import service.escalation.EscalationService.Protocol.Escalate
import service.core.ActorDefaults
import service.task.TaskListReadModelActor.Protocol.{ TaskList, GetTask }
import play.api.libs.ws.WS
import play.api.libs.json.{ JsObject, JsValue, Json }
import controllers.web.DomainSerializers
import service.task.ReClaim

class EscalationService(taskModel: ActorRef) extends Actor with ActorLogging with ActorDefaults {

  def receive = {
    case Escalate(id, taskActor) => {
      log.info("escalate task: " + id)
      ask(taskModel, GetTask(id)).mapTo[TaskList].flatMap { taskList =>
        val json = Json.toJson(taskList.elems.head)(DomainSerializers.taskViewWrites)
        callWithRetry(WS.url("http://localhost:3000/escalation").put(Json.obj("task" -> json)))
      }.map(response => buildReply(response.json)) pipeTo taskActor
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
}

object EscalationService {
  val actorName = "EscalationService"

  def actorPath = s"/user/$actorName"

  def props(taskModel: ActorRef): Props = Props(classOf[EscalationService], taskModel)

  object Protocol {
    case class Escalate(id: String, taskActor: ActorRef)
  }
}
