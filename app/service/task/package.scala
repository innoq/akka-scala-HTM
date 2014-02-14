package service

package object task {

  // TaskManager
  // commands
  sealed trait TaskManagerCommands
  case class CreateTask(input: TaskData, taskType: String, role: Option[String] = None, userId: Option[String] = None, delegatedUser: Option[String] = None) extends TaskManagerCommands
  case class TaskCommand(taskId: String, command: Command) extends TaskManagerCommands

  // events
  sealed trait TaskManagerEvents
  case class NoSuchTask(id: String)

  // states:
  sealed trait TaskState
  case object Created extends TaskState
  case object Ready extends TaskState
  case object Reserved extends TaskState
  case object InProgress extends TaskState
  case object Completed extends TaskState
  case object Obsolete extends TaskState

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

  type TaskData = Map[String, String]

  // commands:
  sealed trait Command
  case class Init(taskId: String, taskType: String, input: TaskData = Map.empty, role: Option[String] = None, userId: Option[String] = None, delegatedUser: Option[String] = None)
  case class Claim(userId: String) extends Command
  case object Release extends Command
  case object Start extends Command
  case object Stop extends Command
  case class Complete(result: TaskData = Map.empty) extends Command
  case object Skip extends Command

}
