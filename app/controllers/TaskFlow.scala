package controllers

import akka.pattern.ask
import play.api._
import play.api.mvc._
import service.task._
import scala.concurrent.ExecutionContext.Implicits.global
import service.task.Task.Protocol._
import play.api.libs.json._
import scala.concurrent.Future
import controllers.web.DefaultController
import controllers.web.HalLinkBuilder._

object TaskFlow extends DefaultController {

  def createTaskAction = Action.async(parse.json) { implicit request =>
    val task = Json.fromJson(request.body)(taskAttributeReads).flatMap(task => Json.fromJson[CreateTask](task))
    task.fold(errorMsg => {
      Logger.warn("task creation failed" + errorMsg.toString())
      Future.successful(BadRequest(error(errorMsg)))
    }, createTask)
  }

  def createTask(task: CreateTask)(implicit request: Request[_]) = {
    Logger.info("new task " + task)
    val result = ask(taskManagerActor, task).mapTo[TaskInitialized]
    result.map { task =>
      Created {
        hal(task.taskModel, links(new TaskView(task.taskModel, task.state)))
      }.withHeaders("Location" -> routes.TaskView.lookup(task.taskModel.id).absoluteURL())
    }
  }

  def claim(taskId: String) = Action.async(parse.json) { request =>
    Logger.info(s"claim task $taskId")
    (request.body \ "user").asOpt[String]
      .fold(Future.successful(BadRequest(error("missing user attribute")))) { user =>
        stateChangeWithDefaultLinks(taskId, Claim(user))
      }
  }

  def start(taskId: String) = Action.async { request =>
    Logger.info(s"start working on task $taskId")
    stateChangeWithDefaultLinks(taskId, Start)
  }

  def complete(taskId: String) = Action.async(parse.json) { request =>
    Logger.info(s"complete task $taskId")
    (request.body \ "output").asOpt[JsObject]
      .fold(stateChange(taskId, Complete(EmptyTaskData))(a => Ok)) { output =>
        stateChangeWithDefaultLinks(taskId, Complete(output))
      }
  }

  def release(taskId: String) = Action.async { request =>
    Logger.info(s"release task $taskId")
    stateChangeWithDefaultLinks(taskId, Release)
  }

  def stop(taskId: String) = Action.async { request =>
    Logger.info(s"stop work on task $taskId")
    stateChangeWithDefaultLinks(taskId, Stop)
  }

  def skip(taskId: String) = Action.async { request =>
    Logger.info(s"skip task $taskId")
    stateChangeWithDefaultLinks(taskId, Skip)
  }

  def stateChangeWithDefaultLinks(taskId: String, command: Command) = {
    stateChange(taskId, command)(task => Ok(hal(task, links(task))))
  }

  def stateChange(taskId: String, msg: Command)(render: TaskView => SimpleResult) = {
    askDefault(taskManagerActor, TaskCommand(taskId, msg)) {
      case e: InvalidCommandRejected => BadRequest(error("invalid state change rejected"))
      case e: TaskEvent => render(new TaskView(e.taskModel, e.state))
      case e: NoSuchTask => NotFound
    }
  }
}
