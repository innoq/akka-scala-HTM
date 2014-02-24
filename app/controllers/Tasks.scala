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
    result.map { task =>
      Created {
        hal(task.taskModel, links(new TaskView(task.taskModel, task.state)))
      }
    }
  }

  def claim(taskId: String) = Action.async(parse.json) { request =>
    Logger.info(s"claim task $taskId")
    (request.body \ "user").asOpt[String]
      .fold(Future.successful(BadRequest(error("missing user attribute")))) { user =>
        stateChange(taskId, Claim(user)) { task =>
          Ok(hal(task, links(task)))
        }
      }
  }

  def start(taskId: String) = Action.async(parse.json) { request =>
    Logger.info(s"start working on task $taskId")
    stateChange(taskId, Start) { task =>
      Ok(hal(task, links(task)))
    }
  }

  def complete(taskId: String) = Action.async(parse.json) { request =>
    Logger.info(s"complete task $taskId")
    (request.body \ "output").asOpt[JsObject]
      .fold(stateChange(taskId, Complete(EmptyTaskData))(a => Ok)) { output =>
        stateChange(taskId, Complete(output))(task => Ok(hal(task, links(task))))
      }
  }

  def release(taskId: String) = Action.async(parse.json) { request =>
    Logger.info(s"release task $taskId")
    stateChange(taskId, Release) { task =>
      Ok(hal(task, links(task)))
    }
  }

  def stop(taskId: String) = Action.async(parse.json) { request =>
    Logger.info(s"stop work on task $taskId")
    stateChange(taskId, Stop)(task => Ok(hal(task, links(task))))
  }

  def skip(taskId: String) = Action.async(parse.json) { request =>
    Logger.info(s"skip task $taskId")
    stateChange(taskId, Skip)(task => Ok(hal(task, links(task))))
  }

  def stateChange(taskId: String, msg: Command)(render: TaskView => SimpleResult) = {
    askDefault(taskManagerActor, TaskCommand(taskId, msg)) {
      case e: InvalidCommandRejected => BadRequest(error("invalid state change rejected"))
      case e: TaskEvent => render(new TaskView(e.taskModel, e.state))
      case e: NoSuchTask => NotFound
    }
  }
}
