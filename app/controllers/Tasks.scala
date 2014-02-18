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
    val result = ask(taskManagerActor, task).mapTo[TaskInitialized]
    result.map { task => Ok(taskToJson(task.taskModel)) }
  }

  def claim(taskId: String) = Action.async(parse.json) { request =>
    Logger.info(s"claim task $taskId")
    (request.body \ "user").asOpt[String]
      .fold(Future.successful(BadRequest("missing user attribute")))(user => claimTask(taskId, user))
  }

  def claimTask(taskId: String, user: String) = {
    ask(taskManagerActor, TaskCommand(taskId, Claim(user))).map {
      case TaskClaimed(model) => Ok(taskToJson(model))
      case e: NoSuchTask => NotFound
    }.recover { case e: Exception => InternalServerError(e.getMessage) }
  }

  def start(taskId: String) = Action.async(parse.json) { request =>
    Logger.info(s"start working on task $taskId")
    ask(taskManagerActor, TaskCommand(taskId, Start)).map {
      case TaskStarted(model) => Ok(taskToJson(model))
      case e: NoSuchTask => NotFound
    }.recover { case e: Exception => InternalServerError(e.getMessage) }
  }

  def lookup(taskId: String) = Action.async { request =>
    ask(readModelActor, GetTask(taskId)) map {
      case TaskList(tasks) if !tasks.isEmpty => Ok(taskToJson(tasks.head))
      case e: NotFound => NotFound
      case e => InternalServerError(e.toString)
    }
  }

  def list(userId: Option[String]) = Action.async { request =>
    lookupTaskList(userId) map {
      case tasks: TaskList => Ok(toJson(tasks))
      case TaskListUnavailable => ServiceUnavailable("task list is not available; try again later")
      case e => InternalServerError(e.toString)
    }
  }

  def lookupTaskList(userId: Option[String]) = {
    ask(readModelActor, GetTaskList(userId))
  }

  def taskManagerActor = Akka.system.actorSelection(TaskManager.actorPath)

  def readModelActor = Akka.system.actorSelection(TaskListReadModelActor.actorPath)
}
