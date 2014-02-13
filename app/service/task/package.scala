package service

package object task {

  // TaskManager
  // commands
  sealed trait TaskManagerCommands
  case class CreateTask(input: TaskData) extends TaskManagerCommands
  case class TaskCommand(taskId: String, command: Command) extends TaskManagerCommands

  // events
  sealed trait TaskManagerEvents
  case class NoSuchTask(id: String)

  // states:
  private[task] sealed trait TaskState
  private[task] case object Created extends TaskState
  private[task] case object Ready extends TaskState
  private[task] case object Reserved extends TaskState
  private[task] case object InProgress extends TaskState
  private[task] case object Completed extends TaskState
  private[task] case object Obsolete extends TaskState

  private[task] sealed abstract class Data {
    def taskId: String
  }
  case class UninitializedData(taskId: String) extends Data
  case class InitialData(taskId: String, input: TaskData) extends Data
  case class ClaimedData(taskId: String, input: TaskData, assigneeId: String) extends Data
  case class CompletedData(taskId: String, input: TaskData, assigneeId: String, result: TaskData) extends Data
  case class EmptyData(taskId: String) extends Data

  type TaskData = Map[String, String]

  // commands:
  sealed trait Command
  case class Init(taskId: String, input: TaskData = Map.empty)
  case class Claim(userId: String) extends Command
  case object Release extends Command
  case object Start extends Command
  case object Stop extends Command
  case class Complete(result: TaskData = Map.empty) extends Command
  case object Skip extends Command

}
