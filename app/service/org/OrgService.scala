package service.org

import akka.actor.{ Props, Actor }
import service.task.{ FilteredTask, TaskModel }
import service.org.OrgService.Protocol.{ FilteredTasks, FilterTasks }
import play.api.libs.ws.{ Response, WS }
import play.api.libs.json._
import akka.pattern.CircuitBreaker

class OrgService extends Actor {
  import concurrent.duration._
  import context.dispatcher
  val breaker =
    new CircuitBreaker(context.system.scheduler,
      maxFailures = 5,
      callTimeout = 10.seconds,
      resetTimeout = 1.minute) onOpen notifyOnOpen

  private def notifyOnOpen() = {

  }

  def receive = {
    case FilterTasks(userId, tasks) =>
      import context.dispatcher
      import akka.pattern.pipe
      val responseF = WS.url("http://10.100.100.172:3001/org").put(buildRequest(userId, tasks))
      responseF map responseToFilteredTasks(userId) pipeTo sender
  }

  private def responseToFilteredTasks(userId: String)(resp: Response): FilteredTasks = {
    import OrgService._
    println(resp.body)
    val r = resp.json.as[OrgResponse](orgResponseReads)
    FilteredTasks(userId, r.tasks)
  }

  private def buildRequest(userId: String, tasks: Vector[TaskModel]): JsObject = {
    Json.obj(
      "user_id" -> userId.toInt,
      "tasks" -> (tasks map taskToJson)
    )
  }

  private def taskToJson(t: TaskModel): JsObject = {
    val fields = Vector(
      "id" -> Some(t.id),
      "state" -> Some(t.state.toString.toLowerCase),
      "role" -> t.role,
      "user_id" -> t.userId,
      "delegated_user" -> t.delegatedUser
    ) collect { case (key, Some(value)) => key -> JsString(value) }
    JsObject(fields)
  }

}

object OrgService {

  def props(): Props = Props[OrgService]

  private[org] case class OrgResponse(tasks: Vector[FilteredTask])
  private[org] implicit val filteredTaskReads = Json.format[FilteredTask]
  private[org] implicit val orgResponseReads = Json.format[OrgResponse]

  object Protocol {
    case class FilterTasks(userId: String, tasks: Vector[TaskModel])
    case class FilteredTasks(userId: String, tasks: Vector[FilteredTask])
  }
}
