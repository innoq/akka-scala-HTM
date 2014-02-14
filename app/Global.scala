import play.api._
import play.api.Play.current
import play.api.libs.concurrent._
import service.task.{ TaskListReadModelActor, TaskManager }

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    Akka.system.actorOf(TaskManager.props(), name = "TaskManager")
    Akka.system.actorOf(TaskListReadModelActor.props(), name = "TaskListReadModelManager")
  }


}
