import play.api._
import play.api.Play.current
import play.api.libs.concurrent._
import service.task.TaskManager

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    Akka.system.actorOf(TaskManager.props(), name = "TaskManager")
  }
}
