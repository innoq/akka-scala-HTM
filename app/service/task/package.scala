package service

import _root_.org.joda.time.DateTime
import play.api.libs.json.{ Json, JsObject }

package object task {

  // TaskManager
  // commands
  sealed trait TaskManagerCommands
  case class CreateTask(input: TaskData, taskType: String, startDeadline: Option[DateTime], completionDeadline: Option[DateTime], role: Option[String] = None, userId: Option[String] = None, delegatedUser: Option[String] = None) extends TaskManagerCommands
  case class TaskLookup(taskId: String)
  case class TaskCommand(taskId: String, command: Command) extends TaskManagerCommands

  // events
  sealed trait TaskManagerEvents
  case class NoSuchTask(id: String)

  // states:
  sealed abstract class TaskState(val name: String)
  case object Created extends TaskState("created")
  case object Ready extends TaskState("ready")
  case object Reserved extends TaskState("reserved")
  case object InProgress extends TaskState("in progress")
  case object Completed extends TaskState("completed")
  case object Obsolete extends TaskState("obsolete")

  private[task] sealed abstract class Data {
    def taskId: String
  }
  trait TaskIdDelegate { self: Data =>
    def taskData: TaskModel
    def taskId = taskData.id
  }
  case class UninitializedData(taskId: String) extends Data
  case class InitialData(taskData: TaskModelImpl) extends Data with TaskIdDelegate
  case class ClaimedData(taskData: TaskModelImpl) extends Data with TaskIdDelegate
  case class CompletedData(taskData: TaskModelImpl) extends Data with TaskIdDelegate
  case class EmptyData(taskId: String) extends Data

  type TaskData = JsObject

  lazy val EmptyTaskData = Json.parse("{ }").as[JsObject]

  // commands:
  sealed trait Command
  case class Init(taskId: String, taskType: String, startDeadline: Option[DateTime], completionDeadline: Option[DateTime], input: TaskData = EmptyTaskData, role: Option[String] = None, userId: Option[String] = None, delegatedUser: Option[String] = None)
  case class Claim(userId: String) extends Command
  case object Release extends Command
  case object Start extends Command
  case object Stop extends Command
  case class Complete(result: TaskData = EmptyTaskData) extends Command
  case object Skip extends Command

}
