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
import com.fasterxml.jackson.annotation.JsonValue

// imports required functional generic structures

object Tasks extends Controller {

  def createTask = Action.async(parse.json) { request =>
    import Task.Protocol._
    val task = request.body \ "task"
    val input = task \ "input"
    val userId = (task \ "user_id").asOpt[Int]
    val role = (task \ "role").asOpt[String]
    val delegatedUser = (task \ "delegated_user").asOpt[String]
    val inputAsMap = input.as[JsObject].fields.filter {
      case (_, e: JsString) => true
      case _ => false
    }.map { case (key, value: JsValue) => (key -> value.as[String]) }.toMap
    val manager = Akka.system.actorSelection("/user/TaskManager")
    val task1: CreateTask = CreateTask(inputAsMap, role, userId.map(_.toString), delegatedUser)
    Logger.info("new task " + task1)
    val result = ask(manager, task1)(2 seconds).mapTo[TaskInitialized]
    result.map { task => Ok(task.toString()).withHeaders("Access-Control-Allow-Origin" -> "*") }
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

  def list(userId: String) = Action.async { request =>
    val manager = Akka.system.actorSelection("/user/TaskListReadModelManager")
    val result = ask(manager, GetTaskList(userId))(2 seconds).mapTo[TaskList]
    val taskReplies = result.map(toJson)
    taskReplies.map(json => Ok(json).withHeaders("Access-Control-Allow-Origin" -> "*"))
  }

}
