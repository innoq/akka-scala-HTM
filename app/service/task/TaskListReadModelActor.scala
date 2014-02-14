package service.task

import akka.actor.{ ActorRef, ActorLogging, Props, Actor }
import akka.pattern._
import scala.concurrent.duration._
import service.task.Task.Protocol.{ TaskInitialized, TaskEvent }
import service.task.TaskListReadModelActor.Protocol.{ TaskList, GetTaskList }
import service.org.OrgService.Protocol.{ FilteredTasks, FilterTasks }

class TaskListReadModelActor(val orgServer: ActorRef) extends Actor with ActorLogging {

  implicit def dis = this.context.dispatcher

  this.context.system.eventStream.subscribe(self, classOf[TaskEvent])

  var model = Map.empty[String, TaskView]

  def receive = {
    case TaskInitialized(taskData, taskId) => {
      model = model + (taskId -> new TaskView(TaskModel(id = taskId, taskData = taskData), Ready))
    }
    case GetTaskList(userId) => {
      val taskViews = model.values.toVector
      val filteredTasks = ask(orgServer, FilterTasks(userId, taskViews))(2.seconds)
      val filteredTaskViews = filteredTasks.map {
        case FilteredTasks(_, tasks) => tasks.map { case FilteredTask(id, _) => model(id) }
      }
      filteredTaskViews pipeTo sender
    }
  }

}

object TaskListReadModelActor {

  def props(orgServer: ActorRef) = Props(classOf[TaskListReadModelActor], orgServer)

  object Protocol {
    case class GetTaskList(userId: String)
    case class TaskList(elems: Seq[TaskView])
  }
}
