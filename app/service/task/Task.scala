package service.task

import akka.actor.{ Props, FSM, Actor }
import service.task.Task.Protocol._
import service.task.Task.Protocol.TaskClaimed
import service.task.Task.Protocol.TaskInitialized

class Task extends Actor with FSM[TaskState, Data] {

  startWith(Created, UninitializedData)

  when(Created) {
    case Event(Init(input), data) =>
      goto(Ready) using InitialData(input) replying TaskInitialized(input)
  }

  when(Ready) {
    case Event(Claim(userId), InitialData(input)) =>
      goto(Reserved) using ClaimedData(input, userId) replying TaskClaimed
  }

  when(Reserved) {
    case Event(Start, data) =>
      goto(InProgress) using data replying TaskStarted
    case Event(Release, ClaimedData(input, _)) =>
      goto(Ready) using InitialData(input) replying TaskReleased
  }

  when(InProgress) {
    case Event(Complete(result), ClaimedData(input, userId)) =>
      goto(Completed) using CompletedData(input, userId, result) replying TaskCompleted(result)
    case Event(Stop, _) =>
      goto(Reserved) replying TaskStopped
  }

  whenUnhandled {
    case Event(Skip, _) =>
      goto(Obsolete) using EmptyData replying TaskSkipped
    case Event(cmd: Command, _) =>
      stay replying InvalidCommandRejected(cmd, stateName)
  }

  initialize()

}

object Task {

  def props() = Props[Task]

  object Protocol {
    case class TaskInitialized(input: TaskData)
    case class TaskClaimed(assigneeId: String)
    case object TaskStarted
    case object TaskReleased
    case class TaskCompleted(result: TaskData)
    case object TaskStopped
    case object TaskSkipped
    case class InvalidCommandRejected(cmd: Command, state: TaskState)
  }
}
