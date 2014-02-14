package service.org

import akka.actor.{ Props, Actor }
import service.task.{ TaskView, FilteredTask }
import service.org.OrgService.Protocol.{ OrgServiceUnreachable, FilteredTasks, FilterTasks }
import play.api.libs.ws.{ Response, WS }
import play.api.libs.json._
import akka.pattern.CircuitBreaker
import akka.pattern.CircuitBreakerOpenException

class OrgService extends Actor {
  import concurrent.duration._
  import context.dispatcher
  val breaker =
    new CircuitBreaker(context.system.scheduler,
      maxFailures = 5,
      callTimeout = 10.seconds,
      resetTimeout = 1.minute) onOpen notifyOnOpen

  private def notifyOnOpen() = {
    //
  }

  def receive = {
    case FilterTasks(userId, tasks) =>
      import context.dispatcher
      import akka.pattern.pipe
      val responseF = breaker withCircuitBreaker {
        implicit val success = new retry.Success[Response](_.status == 200)
        import retry.Defaults._
        retry.Directly(3) { () =>
          WS.url("http://10.100.100.172:3001/org").put(buildRequest(userId, tasks))
        }
      }
      responseF map responseToFilteredTasks(userId) recover {
        case e: CircuitBreakerOpenException => OrgServiceUnreachable
      } pipeTo sender
  }

  private def responseToFilteredTasks(userId: String)(resp: Response): FilteredTasks = {
    import OrgService._
    val r = resp.json.as[OrgResponse](orgResponseReads)
    FilteredTasks(userId, r.tasks)
  }

  private def buildRequest(userId: String, tasks: Vector[TaskView]): JsObject = {
    Json.obj(
      "user_id" -> userId.toInt,
      "tasks" -> (tasks map taskToJson)
    )
  }

  private def taskToJson(t: TaskView): JsObject = {
    val fields = Vector(
      "id" -> Some(t.id),
      "state" -> Some(t.taskState.toString.toLowerCase),
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
    case class FilterTasks(userId: String, tasks: Vector[TaskView])
    case class FilteredTasks(userId: String, tasks: Vector[FilteredTask])
    case object OrgServiceUnreachable
  }
}