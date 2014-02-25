package controllers

import controllers.web.DefaultController
import play.api.mvc.Action
import service.task.TaskListReadModelActor.Protocol._
import play.api.libs.json.Json
import controllers.web.HalLinkBuilder._
import service.task.TaskListReadModelActor.Protocol.NotFound
import service.task.TaskListReadModelActor.Protocol.TaskList
import service.task.TaskListReadModelActor.Protocol.GetTaskList
import service.task.TaskListReadModelActor.Protocol.GetTask

object TaskView extends DefaultController {

  def lookup(taskId: String) = Action.async { request =>
    askDefault(readModelActor, GetTask(taskId)) {
      case TaskList(tasks) if !tasks.isEmpty => Ok(hal(tasks.head, links(tasks.head)))
      case e: NotFound => NotFound
    }
  }

  def list(userId: Option[String]) = Action.async { request =>
    askDefault(readModelActor, GetTaskList(userId)) {
      case tasks: TaskList => Ok {
        val taskHalResources = tasks.elems.map(task => hal(task, links(task)))
        hal(Json.obj("amount" -> tasks.elems.size),
          embedded = "tasks" -> taskHalResources.toVector,
          links = "self" -> routes.TaskView.list(None))
      }
      case TaskListUnavailable => ServiceUnavailable(error("task list is not available; try again later"))
    }
  }

}
