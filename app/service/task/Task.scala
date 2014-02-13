package service.task

import service.task.Task.Protocol._
import service.task.Task.Protocol.TaskClaimed
import service.task.Task.Protocol.TaskInitialized
import akka.actor.{ ActorLogging, FSM, Actor, Props }

class Task extends Actor with FSM[TaskState, Data] with ActorLogging {

  startWith(Created, UninitializedData(""))

  when(Created) {
    case Event(Init(taskId, input), data) =>
      goto(Ready) using InitialData(taskId, input) replying TaskInitialized(input, taskId)
  }

  when(Ready) {
    case Event(Claim(userId), InitialData(taskId, input)) =>
      goto(Reserved) using ClaimedData(taskId, input, userId) replying TaskClaimed(userId, taskId)
  }

  when(Reserved) {
    case Event(Start, data) =>
      goto(InProgress) using data replying TaskStarted
    case Event(Release, ClaimedData(taskId, input, _)) =>
      goto(Ready) using InitialData(taskId, input) replying TaskReleased(taskId)
  }

  when(InProgress) {
    case Event(Complete(result), ClaimedData(taskId, input, userId)) =>
      goto(Completed) using CompletedData(taskId, input, userId, result) replying TaskCompleted(result, taskId)
    case Event(Stop, d) =>
      goto(Reserved) replying TaskStopped(d.taskId)
  }

  when(Obsolete) {
    case _ => stay()
  }

  when(Completed) {
    case _ => stay()
  }

  whenUnhandled {
    case Event(Skip, d) =>
      goto(Obsolete) using EmptyData(d.taskId) replying TaskSkipped(d.taskId)
    case Event(cmd: Command, data) =>
      stay replying InvalidCommandRejected(cmd, stateName, data.taskId)
  }

  onTransition {
    case e => log.debug("transition => $e")
  }

  initialize()

}

object Task {

  def props() = Props[Task]

  object Protocol {
    sealed abstract class TaskEvent(val taskId: String)
    case class TaskInitialized(input: TaskData, taskid: String) extends TaskEvent(taskid)
    case class TaskClaimed(assigneeId: String, taskid: String) extends TaskEvent(taskid)
    case class TaskStarted(taskid: String) extends TaskEvent(taskid)
    case class TaskReleased(taskid: String) extends TaskEvent(taskid)
    case class TaskCompleted(result: TaskData, taskid: String) extends TaskEvent(taskid)
    case class TaskStopped(taskid: String) extends TaskEvent(taskid)
    case class TaskSkipped(taskid: String) extends TaskEvent(taskid)
    case class InvalidCommandRejected(cmd: Command, state: TaskState, taskid: String) extends TaskEvent(taskid)
  }
}
