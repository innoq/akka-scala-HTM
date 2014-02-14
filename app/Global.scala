import play.api._
import play.api.Play.current
import play.api.libs.concurrent._
import service.org.OrgService
import service.task
import service.task.{ TaskListReadModelActor, TaskManager }

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    Akka.system.actorOf(TaskManager.props(), name = TaskManager.actorName)
    val orgService = Akka.system.actorOf(OrgService.props(), name = OrgService.actorName)
    Akka.system.actorOf(TaskListReadModelActor.props(orgService), name = TaskListReadModelActor.actorName)
  }

}
