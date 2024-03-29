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
  sealed abstract class TaskState(val name: String, val isClaimed: Boolean, val isFinalState: Boolean)
  case object Created extends TaskState("created", false, false)
  case object Ready extends TaskState("ready", false, false)
  case object Reserved extends TaskState("reserved", true, false)
  case object InProgress extends TaskState("in progress", true, false)
  case object Completed extends TaskState("completed", false, true)
  case object Obsolete extends TaskState("obsolete", false, true)

  private[task] sealed abstract class Data {
    def taskId: String
  }
  trait TaskIdDelegate { self: Data =>
    def taskData: TaskModel
    def taskId = taskData.id
  }
  case class UninitializedData(taskId: String) extends Data
  case class ProcessData(taskData: TaskModelImpl) extends Data with TaskIdDelegate
  case class EmptyData(taskId: String) extends Data

  type TaskData = JsObject

  lazy val EmptyTaskData = Json.parse("{ }").as[JsObject]

  // commands:
  sealed trait Command
  case class Init(taskId: String, taskType: String, startDeadline: Option[DateTime], completionDeadline: Option[DateTime], input: TaskData = EmptyTaskData, role: Option[String] = None, userId: Option[String] = None, delegatedUser: Option[String] = None)
  case class Claim(userId: String) extends Command
  case class ReClaim(userId: String) extends Command
  case object Release extends Command
  case object Start extends Command
  case object Stop extends Command
  case class Complete(result: TaskData = EmptyTaskData) extends Command
  case object Skip extends Command

}
