package controllers

import controllers.web.{ Self, SelfTask, DefaultController }
import play.api.mvc.{ SimpleResult, Request, Action }
import service.task.TaskListReadModelActor.Protocol._
import play.api.libs.json.Json
import service.task.TaskListReadModelActor.Protocol.NotFound
import service.task.TaskListReadModelActor.Protocol.TaskList
import service.task.TaskListReadModelActor.Protocol.GetTaskList
import service.task.TaskListReadModelActor.Protocol.GetTask
import service.task.TaskLinkVersionView

object TaskView extends DefaultController {

  def lookup(taskId: String) = Action.async { implicit request =>
    askDefault(readModelActor, GetTask(taskId)) {
      case TaskList(tasks) if !tasks.isEmpty => createResponse(tasks.head)
      case e: NotFound => NotFound
    }
  }

  def createResponse(task: TaskLinkVersionView)(implicit request: Request[_]) = {
    val etag = task.version.toString
    withEtag(etag)(Ok(hal(task, SelfTask(task.id).list)))
  }

  def withEtag(etag: String)(result: => SimpleResult)(implicit request: Request[_]) = {
    request.headers.get("If-None-Match")
      .filter(_ == etag)
      .map(_ => NotModified)
      .getOrElse(result).withHeaders("ETag" -> etag)
  }

  def list(userId: Option[String]) = Action.async { request =>
    askDefault(readModelActor, GetTaskList(userId)) {
      case tasks: TaskList => createResponse(tasks)
      case TaskListUnavailable => ServiceUnavailable(error("task list is not available; try again later"))
    }
  }

  def createResponse(taskList: TaskList) = {
    Ok {
      val taskHalResources = taskList.elems.map(task => hal(task, SelfTask(task.id) +: task.links))
      hal(Json.obj("amount" -> taskList.elems.size),
        embedded = "tasks" -> taskHalResources.toVector,
        links = Self(routes.TaskView.list(None).url).list)
    }
  }
}
