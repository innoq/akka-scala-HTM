import controllers.web.{ ResponseBuilder, Hal }
import play.api._
import play.api.mvc._
import play.api.Play.current
import play.api.libs.concurrent._
import play.filters.gzip.GzipFilter
import scala.concurrent.Future
import service.org.OrgService
import service.task.{ TaskListReadModelActor, TaskManager }

object Global extends WithFilters(new GzipFilter()) with GlobalSettings with Rendering with AcceptExtractors with ResponseBuilder with Results {

  override def onStart(app: Application) {
    Akka.system.actorOf(TaskManager.props(), name = TaskManager.actorName)
    val orgService = Akka.system.actorOf(OrgService.props(), name = OrgService.actorName)
    Akka.system.actorOf(TaskListReadModelActor.props(orgService), name = TaskListReadModelActor.actorName)
  }

  override def onBadRequest(request: RequestHeader, errorMsg: String) = {
    render.async {
      case Accepts.Json() | Hal.accept() => Future.successful(BadRequest(error(errorMsg)))
      case Accepts.Html => super.onBadRequest(request, errorMsg)
    }(request)
  }

  override def onError(request: RequestHeader, ex: Throwable) = {
    render.async {
      case Accepts.Json() | Hal.accept() => Future.successful(InternalServerError(failure(ex)))
      case Accepts.Html => super.onError(request, ex)
    }(request)
  }
}
