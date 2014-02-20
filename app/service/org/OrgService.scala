package service.org

import akka.actor.{ ActorLogging, Props, Actor }
import service.task.{ TaskView, FilteredTask }
import service.org.OrgService.Protocol.{ OrgServiceUnreachable, FilteredTasks, FilterTasks }
import play.api.libs.ws.{ Response, WS }
import play.api.libs.json._
import akka.pattern.CircuitBreaker
import akka.pattern.CircuitBreakerOpenException
import controllers.web.DomainSerializers

class OrgService extends Actor with ActorLogging {
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
    case FilterTasks(userId, tasks) if tasks.isEmpty => sender ! FilteredTasks(userId.toInt, Vector.empty)
    case FilterTasks(userId, tasks: Vector[TaskView]) =>
      import context.dispatcher
      import akka.pattern.pipe
      val responseF = breaker withCircuitBreaker {
        implicit val success = new retry.Success[Response](_.status == 200)
        import retry.Defaults._
        retry.Directly(3) { () =>
          WS.url("http://localhost:3000/org").put(buildRequest(userId, tasks))
        }
      }
      responseF map responseToFilteredTasks(userId) recover {
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
