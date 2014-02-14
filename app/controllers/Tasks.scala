package controllers

import akka.pattern.ask
import play.api._
import play.api.Play.current
import play.api.mvc._
import play.api.libs.concurrent._
import scala.concurrent.duration._
import service.task._
import scala.concurrent.ExecutionContext.Implicits.global
import service.task.Task.Protocol._
import service.task.TaskListReadModelActor.Protocol._
import play.api.libs.json._
import scala.concurrent.Future
import controllers.web.DomainSerializers

object Tasks extends Controller with DomainSerializers {

  def createTaskAction = Action.async(parse.json) { request =>
    val task = (request.body \ "task").validate[JsObject].flatMap(task => Json.fromJson[CreateTask](task))
    task.fold(error => Future.successful(BadRequest("")), createTask)
  }

  def createTask(task: CreateTask) = {
    Logger.info("new task " + task)
    val manager = Akka.system.actorSelection("/user/TaskManager")
    val result = ask(manager, task)(2 seconds).mapTo[TaskInitialized]
    result.map { task => Ok(task.toString()) }
  }

  def list(userIdParam: String) = Action.async { request =>
    val userId = if (userIdParam == "-1") None else Some(userIdParam)
    val manager = Akka.system.actorSelection("/user/TaskListReadModelManager")
    val result = ask(manager, GetTaskList(userId))(2 seconds).mapTo[Either[TaskListUnavailable.type, TaskList]]
    result map {
      case Right(taskList) => Ok(toJson(taskList))
      case Left(TaskListUnavailable) => ServiceUnavailable("task list is not available; try again later")
    }
  }

}
