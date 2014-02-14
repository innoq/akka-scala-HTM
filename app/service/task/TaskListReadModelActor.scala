package service.task

import akka.actor.{ ActorRef, ActorLogging, Props, Actor }
import akka.pattern._
import scala.concurrent.duration._
import service.task.Task.Protocol.{ TaskInitialized, TaskEvent }
import service.task.TaskListReadModelActor.Protocol.{ TaskList, GetTaskList }
import service.org.OrgService.Protocol.{ FilteredTasks, FilterTasks }
import scala.concurrent.Future

class TaskListReadModelActor(val orgServer: ActorRef) extends Actor with ActorLogging {

  implicit def dis = this.context.dispatcher

  this.context.system.eventStream.subscribe(self, classOf[TaskEvent])

  var model = Map.empty[String, TaskView]

  def receive = {
    case TaskInitialized(taskModel) => {
      model = model + (taskModel.id -> new TaskView(taskModel, Ready))
    }
    case GetTaskList(userId) => {
      val taskViews = model.values.toVector
      val filteredTaskViews = userId match {
        case None => Future.successful(TaskList(taskViews))
        case Some(userId) => {
          val filteredTasks = ask(orgServer, FilterTasks(userId, taskViews))(2.seconds)
          filteredTasks.map {
            case FilteredTasks(_, tasks) => TaskList(tasks.map { case FilteredTask(id, _) => model(id) })
          }
        }
      }
      filteredTaskViews pipeTo sender
    }
  }

}

object TaskListReadModelActor {

  def props(orgServer: ActorRef) = Props(classOf[TaskListReadModelActor], orgServer)

  object Protocol {
    case class GetTaskList(userId: Option[String])
    case class TaskList(elems: Seq[TaskView])
  }
}
