package controllers.web

import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.json.JsString
import play.api.libs.json.JsObject
import service.task._
import controllers.Tasks.TaskReply
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import service.task.TaskListReadModelActor.Protocol.TaskList
import service.task.TaskListReadModelActor.Protocol.TaskList
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import service.task.CreateTask

object DomainSerializers extends DomainSerializers

trait DomainSerializers {

  implicit val createTaskReads =
    ((__ \ "input").read[JsObject].map(_.fields.filter {
      case (_, e: JsString) => true
      case _ => false
    }.map { case (key, value: JsValue) => (key -> value.as[String]) }.toMap) and
      (__ \ "type").read[String] and
      (__ \ "role").readNullable[String] and
      (__ \ "user_id").readNullable[Int].map(_.map(_.toString)) and
      (__ \ "delegated_user").readNullable[String])(CreateTask.apply _)

  case class TaskReply(id: String, output: TaskData, input: TaskData, state: String, `type`: String)

  implicit val taskWrites = Json.writes[TaskReply]

  def taskToJson(t: TaskView): JsObject = {
    val fields = Vector(
      "id" -> Some(t.id),
      "state" -> Some(t.taskState.toString.toLowerCase),
      "role" -> t.role,
      "user_id" -> t.userId,
      "delegated_user" -> t.delegatedUser
    ) collect { case (key, Some(value)) => key -> JsString(value) }
    JsObject(fields)
  }

  def taskToJson(t: TaskModel): JsObject = {
    val fields = Vector(
      "id" -> Some(t.id),
      "role" -> t.role,
      "user_id" -> t.userId,
      "delegated_user" -> t.delegatedUser
    ) collect { case (key, Some(value)) => key -> JsString(value) }
    JsObject(fields)
  }

  def toJson(list: TaskList): JsValue = {
    val tasks = list.elems.map { task =>
      val taskState = task.taskState match {
        case Ready => "ready"
        case _ => "other"
      }
      TaskReply(task.id, task.taskData, task.taskData, taskState, task.taskType)
    }
    Json.toJson(Map("tasks" -> tasks))
  }
}

