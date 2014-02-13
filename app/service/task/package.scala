package service

package object task {

  // states:
  sealed trait TaskState
  case object Created extends TaskState
  case object Ready extends TaskState
  case object Reserved extends TaskState
  case object InProgress extends TaskState
  case object Completed extends TaskState
  case object Obsolete extends TaskState

  private[task] sealed trait Data
  case object UninitializedData extends Data
  case class InitialData(input: TaskData) extends Data
  case class ClaimedData(input: TaskData, assigneeId: String) extends Data
  case class CompletedData(input: TaskData, assigneeId: String, result: TaskData) extends Data
  case object EmptyData extends Data

  type TaskData = Map[String, String]

  // commands:
  sealed trait Command
  case class Init(input: TaskData = Map.empty)
  case class Claim(userId: String) extends Command
  case object Release extends Command
  case object Start extends Command
  case object Stop extends Command
  case class Complete(result: TaskData = Map.empty) extends Command
  case object Skip extends Command

}
