package service.task

import akka.actor.{ ActorLogging, Props, Actor }
import service.task.Task.Protocol.{ TaskInitialized, TaskEvent }
import service.task.TaskListReadModelActor.Protocol.{ TaskList, GetTaskList }

class TaskListReadModelActor extends Actor with ActorLogging {

  this.context.system.eventStream.subscribe(self, classOf[TaskEvent])

  var model = Map.empty[String, TaskView]

  def receive = {
    case TaskInitialized(taskData, taskId) => {
      model = model + (taskId -> new TaskView(TaskModel(id = taskId, taskData = taskData), Ready))
    }
    case GetTaskList => this.sender ! TaskList(model.values.toSeq)
  }

}

object TaskListReadModelActor {

  def props() = Props[TaskListReadModelActor]

  object Protocol {
    case object GetTaskList
    case class TaskList(elems: Seq[TaskView])
  }
}
