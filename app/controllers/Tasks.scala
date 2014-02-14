package controllers

import akka.pattern.ask
import play.api._
import play.api.Play.current
import play.api.mvc._
import play.api.libs.concurrent._
import play.api.libs.json._
import scala.concurrent.duration._
import service.task._
import scala.concurrent.ExecutionContext.Implicits.global
import service.task.Task.Protocol._
import service.task.TaskListReadModelActor.Protocol._
import play.api.libs.json._
// imports required functional generic structures

object Tasks extends Controller {

  def createTask = Action.async(parse.json) { request =>
    import Task.Protocol._
    val task = request.body \ "task"
    val input = task \ "input"
    val inputAsMap = input.as[JsObject].fields.map { case (key, value) => (key -> value.toString()) }.toMap
    val manager = Akka.system.actorSelection("/user/TaskManager")
    val result = ask(manager, CreateTask(inputAsMap))(2 seconds).mapTo[TaskInitialized]
    result.map { task => Ok(task.toString()) }
  }

  case class TaskReply(id: String, output: TaskData, input: TaskData, state: String)

  implicit val taskWrites = Json.writes[TaskReply]

  def toJson(list: TaskList): JsValue = {
    val tasks = list.elems.map { task =>
      val taskState = task.taskState match {
        case Ready => "ready"
        case _ => "other"
      }
      TaskReply(task.id, task.taskData, task.taskData, taskState)
    }
    Json.toJson(Map("tasks" -> tasks))
  }

  def list = Action.async { request =>
    val manager = Akka.system.actorSelection("/user/TaskListReadModelManager")
    val result = ask(manager, GetTaskList)(2 seconds).mapTo[TaskList]
    val taskReplies = result.map(toJson)
    taskReplies.map(json => Ok(json))
  }

}
