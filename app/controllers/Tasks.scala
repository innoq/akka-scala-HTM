package controllers

import akka.pattern.ask
import play.api._
import play.api.Play.current
import play.api.mvc._
import play.api.libs.concurrent._
import service.task._
import scala.concurrent.ExecutionContext.Implicits.global
import service.task.Task.Protocol._
import service.task.TaskListReadModelActor.Protocol._
import play.api.libs.json._
import scala.concurrent.Future
import controllers.web.DefaultController

object Tasks extends DefaultController {

  def createTaskAction = Action.async(parse.json) { request =>
    val task = (request.body \ "task").validate[JsObject].flatMap(task => Json.fromJson[CreateTask](task))
    task.fold(error => Future.successful(BadRequest("")), createTask)
  }

  def createTask(task: CreateTask) = {
    Logger.info("new task " + task)
    val manager = Akka.system.actorSelection(TaskManager.actorPath)
    val result = ask(manager, task).mapTo[TaskInitialized]
    result.map { task => Ok(task.toString()) }
  }

  def list(userIdParam: String) = Action.async { request =>
    val userId = if (userIdParam == "-1") None else Some(userIdParam)
    lookupTaskList(userId) map {
      case Right(taskList) => Ok(toJson(taskList))
      case Left(TaskListUnavailable) => ServiceUnavailable("task list is not available; try again later")
    }
  }

  def lookupTaskList(userId: Option[String]) = {
    val manager = Akka.system.actorSelection(TaskListReadModelActor.actorPath)
    ask(manager, GetTaskList(userId)).mapTo[Either[TaskListUnavailable.type, TaskList]]
  }
}
