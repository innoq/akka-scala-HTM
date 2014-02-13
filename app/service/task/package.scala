package service

package object task {

  // states:
  private sealed trait TaskState
  private case object Ready extends TaskState
  private case object Reserved extends TaskState
  private case object InProgress extends TaskState
  private case object Completed extends TaskState
  private case object Obsolete extends TaskState

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
