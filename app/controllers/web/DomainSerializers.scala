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
import play.api.Logger
import org.joda.time.DateTime
import scala.util.Try

object DomainSerializers extends DomainSerializers

trait DomainSerializers {

  implicit val dtReads = new Reads[DateTime] {
    def reads(json: JsValue): JsResult[DateTime] = {
      Try(json.validate[String].map(DateTime.parse))
        .recover { case e => JsError("wrong ISO8601 date format") }
        .get
    }
  }

  implicit val createTaskReads =
    ((__ \ "input").read[JsObject] and
      (__ \ "type").read[String] and
      (__ \ "start_deadline").readNullable[DateTime] and
      (__ \ "completion_deadline").readNullable[DateTime] and
      (__ \ "role").readNullable[String] and
      (__ \ "user_id").readNullable[Int].map(_.map(_.toString)) and
      (__ \ "delegated_user").readNullable[String])(CreateTask.apply _)

  val taskAttributeReads =
    ((__ \ "task")).read[JsObject]

  case class TaskReply(id: String, output: TaskData, input: TaskData, state: String, `type`: String)

  implicit val taskWrites = Json.writes[TaskReply]

  implicit val taskStateWrites = new Writes[TaskState] {
    def writes(o: TaskState): JsValue = JsString(o.name)
  }

  implicit val taskViewWrites = new Writes[TaskView] {
    def writes(o: TaskView): JsValue = {
      val taskModel = Json.toJson(o.taskModel).as[JsObject]
      taskModel + ("state" -> Json.toJson(o.taskState))
    }
  }

  implicit val taskModelWrites: Writes[TaskModel] =
    ((__ \ "id").write[String] and
      (__ \ "taskType").write[String] and
      (__ \ "role").writeNullable[String] and
      (__ \ "user_id").writeNullable[String] and
      (__ \ "delegated_user").writeNullable[String] and
      (__ \ "input").write[TaskData] and
      (__ \ "output").write[TaskData]
    )(unlift(TaskModel.unapply))

  def toJson(list: TaskList): JsValue = {
    val tasks = list.elems.map { task =>
      val taskState = task.taskState.name
      TaskReply(task.id, task.result, task.taskData, taskState, task.taskType)
    }
    val json = Json.toJson(Map("tasks" -> tasks))
    json
  }
}

