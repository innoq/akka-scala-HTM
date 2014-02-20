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
import controllers.web.{ HalDocument, HalLinks, HalLink, DefaultController }
import akka.actor.{ ActorSelection, ActorRef }
import scala.util.control.NonFatal

object Tasks extends DefaultController {

  def createTaskAction = Action.async(parse.json) { request =>
    val task = Json.fromJson(request.body)(taskAttributeReads).flatMap(task => Json.fromJson[CreateTask](task))
    task.fold(errorMsg => {
      Logger.warn("task creation failed" + errorMsg.toString())
      Future.successful(BadRequest(error(errorMsg)))
    }, createTask)
  }

  def createTask(task: CreateTask) = {
    Logger.info("new task " + task)
    val result = ask(taskManagerActor, task).mapTo[TaskInitialized]
    result.map { task => Created(Json.toJson(halDocument(task.taskModel))) }
      .recover { case e: Exception => InternalServerError(failure(e)) }
  }

  def halDocument(taskModel: TaskModel) = {
    HalDocument(HalLinks.links(
      HalLink("claim", routes.Tasks.claim(taskModel.id)),
      HalLink("skip", routes.Tasks.skip(taskModel.id)),
      HalLink("self", routes.Tasks.lookup(taskModel.id))),
      Json.toJson(taskModel).as[JsObject])
  }

  def claim(taskId: String) = Action.async(parse.json) { request =>
    Logger.info(s"claim task $taskId")
    (request.body \ "user").asOpt[String]
      .fold(Future.successful(BadRequest(error("missing user attribute"))))(user => stateChange(taskId, Claim(user)))
  }

  def start(taskId: String) = Action.async(parse.json) { request =>
    Logger.info(s"start working on task $taskId")
    stateChange(taskId, Start)
  }

  def complete(taskId: String) = Action.async(parse.json) { request =>
    Logger.info(s"complete task $taskId")
    (request.body \ "output").asOpt[JsObject]
      .fold(stateChange(taskId, Complete(EmptyTaskData)))(output => stateChange(taskId, Complete(output)))
  }

  def release(taskId: String) = Action.async(parse.json) { request =>
    Logger.info(s"release task $taskId")
    stateChange(taskId, Release)
  }

  def stop(taskId: String) = Action.async(parse.json) { request =>
    Logger.info(s"stop work on task $taskId")
    stateChange(taskId, Stop)
  }

  def skip(taskId: String) = Action.async(parse.json) { request =>
    Logger.info(s"skip task $taskId")
    stateChange(taskId, Skip)
  }

  def stateChange(taskId: String, msg: Command) = {
    askDefault(taskManagerActor, TaskCommand(taskId, msg)) {
      case e: InvalidCommandRejected => BadRequest(error("invalid state change rejected"))
      case e: TaskEvent => Ok(Json.toJson(new TaskView(e.taskModel, e.state)))
      case e: NoSuchTask => NotFound
    }
  }

  def lookup(taskId: String) = Action.async { request =>
    askDefault(readModelActor, GetTask(taskId)) {
      case TaskList(tasks) if !tasks.isEmpty => Ok(Json.toJson(tasks.head))
      case e: NotFound => NotFound
    }
  }

  def list(userId: Option[String]) = Action.async { request =>
    askDefault(readModelActor, GetTaskList(userId)) {
      case tasks: TaskList => Ok(toJson(tasks))
      case TaskListUnavailable => ServiceUnavailable(error("task list is not available; try again later"))
    }
  }

  def askDefault(ref: ActorSelection, msg: AnyRef)(handle: Any => SimpleResult) = {
    ask(ref, msg).map(handle).recover { case e: Exception => InternalServerError(failure(e)) }
  }

  def taskManagerActor = Akka.system.actorSelection(TaskManager.actorPath)

  def readModelActor = Akka.system.actorSelection(TaskListReadModelActor.actorPath)
}
