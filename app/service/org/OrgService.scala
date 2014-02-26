package service.org

import akka.actor.{ ActorLogging, Props, Actor }
import akka.pattern.pipe
import service.task.{ TaskView, FilteredTask }
import service.org.OrgService.Protocol.{ OrgServiceUnreachable, FilteredTasks, FilterTasks }
import play.api.libs.ws.{ Response, WS }
import play.api.libs.json._
import controllers.web.DomainSerializers
import service.core.ActorDefaults
import akka.pattern.CircuitBreakerOpenException

class OrgService extends Actor with ActorLogging with ActorDefaults {

  def receive = {
    case FilterTasks(userId, tasks) if tasks.isEmpty => sender ! FilteredTasks(userId.toInt, Vector.empty)
    case FilterTasks(userId, tasks: Vector[TaskView]) =>
      callWithRetry(WS.url("http://localhost:3000/org")
        .put(buildRequest(userId, tasks))) map responseToFilteredTasks(userId) recover {
        case e: CircuitBreakerOpenException => OrgServiceUnreachable
      } pipeTo sender
  }
  private def responseToFilteredTasks(userId: String)(resp: Response): FilteredTasks = {
    import OrgService._
    log.info(resp.body)
    val r = resp.json.as[OrgResponse](orgResponseReads)
    FilteredTasks(userId.toInt, r.tasks)
  }

  private def buildRequest(userId: String, tasks: Vector[TaskView]): JsObject = {
    implicit val write = DomainSerializers.taskViewWrites
    Json.obj(
      "user_id" -> userId.toInt,
      "tasks" -> Json.toJson(tasks)
    )
  }
}

object OrgService {

  val actorName = "OrgService"

  def actorPath = s"/user/$actorName"

  def props(): Props = Props[OrgService]

  private[org] case class OrgResponse(tasks: Vector[FilteredTask])
  private[org] implicit val filteredTaskReads = Json.format[FilteredTask]
  private[org] implicit val orgResponseReads = Json.format[OrgResponse]

  object Protocol {
    case class FilterTasks(userId: String, tasks: Vector[TaskView])
    case class FilteredTasks(userId: Int, tasks: Vector[FilteredTask])
    case object OrgServiceUnreachable
  }
}
