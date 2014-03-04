package controllers

import akka.pattern.ask
import play.api._
import play.api.mvc._
import service.task._
import scala.concurrent.ExecutionContext.Implicits.global
import service.task.Task.Protocol._
import play.api.libs.json._
import scala.concurrent.Future
import controllers.web.{ SelfTask, Link, DefaultController }

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
        hal(task.taskModel, task.links)
      }.withHeaders("Location" -> routes.TaskView.lookup(task.taskModel.id).absoluteURL())
    }
  }

  def changeAssignee(taskId: String) = Action.async(parse.json) { request =>
    Logger.info(s"set assignee for task task $taskId")
    (request.body \ "user").asOpt[String]
      .fold(Future.successful(BadRequest(error("missing user attribute")))) { user =>
        stateChangeWithDefaultLinks(taskId, Claim(user))
      }
  }

  def removeAssignee(taskId: String) = Action.async { request =>
    Logger.info(s"remove assignee for task task $taskId")
    stateChangeWithDefaultLinks(taskId, Release)
  }

  def start(taskId: String) = Action.async { request =>
    Logger.info(s"start working on task $taskId")
    stateChangeWithDefaultLinks(taskId, Start)
  }

  def stop(taskId: String) = Action.async { request =>
    Logger.info(s"stop work on task $taskId")
    stateChangeWithDefaultLinks(taskId, Stop)
  }

  def skip(taskId: String) = Action.async { request =>
    Logger.info(s"skip task $taskId")
    stateChangeWithDefaultLinks(taskId, Skip)
  }

  def output(taskId: String) = Action.async(parse.json) { request =>
    Logger.info(s"complete task $taskId")
    (request.body \ "output").asOpt[JsObject]
      .fold(stateChange(taskId, Complete(EmptyTaskData))((t, l) => Ok)) { output =>
        stateChangeWithDefaultLinks(taskId, Complete(output))
      }
  }

  def stateChangeWithDefaultLinks(taskId: String, command: Command) = {
    stateChange(taskId, command) { (task, links) =>
      val self = SelfTask(task.id)
      Ok(hal(task, self +: links)).withHeaders("Content-Location" -> self.link)
    }
  }

  def stateChange(taskId: String, msg: Command)(render: (TaskView, Vector[Link]) => SimpleResult) = {
    askDefault(taskManagerActor, TaskCommand(taskId, msg)) {
      case e: InvalidCommandRejected => Conflict {
        val err = error("task state transition not possible, please follow the link relations")
        hal(err, SelfTask(e.taskId).list)
      }
      case e: TaskEvent => render(new TaskView(e.taskModel, e.state), e.links)
      case e: NoSuchTask => NotFound

    }
  }
}
