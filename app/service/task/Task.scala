package service.task

import service.task.Task.Protocol._
import service.task.Task.Protocol.TaskClaimed
import service.task.Task.Protocol.TaskInitialized
import akka.actor.{ ActorLogging, FSM, Actor, Props }

class Task extends Actor with FSM[TaskState, Data] with ActorLogging {

  def publishing(state: TaskEvent) = {
    context.system.eventStream.publish(state)
    state
  }

  startWith(Created, UninitializedData(""))

  when(Created) {
    case Event(Init(taskId, input, role, userId, delegate), data) =>
      goto(Ready) using InitialData(TaskModelImpl(taskId, role, userId, delegate, input)) replying publishing(TaskInitialized(input, taskId))
  }

  when(Ready) {
    case Event(Claim(userId), InitialData(taskModel)) =>
      goto(Reserved) using ClaimedData(taskModel.copy(userId = Some(userId))) replying publishing(TaskClaimed(userId, taskModel.id))
  }

  when(Reserved) {
    case Event(Start, data) =>
      goto(InProgress) replying publishing(TaskStarted(data.taskId))
    case Event(Release, ClaimedData(model)) =>
      goto(Ready) using InitialData(model) replying publishing(TaskReleased(model.id))
  }

  when(InProgress) {
    case Event(Complete(result), ClaimedData(taskData)) =>
      goto(Completed) using CompletedData(taskData) replying publishing(TaskCompleted(result, taskData.id))
    case Event(Stop, d) =>
      goto(Reserved) replying publishing(TaskStopped(d.taskId))
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
      stay replying publishing(InvalidCommandRejected(cmd, stateName, data.taskId))
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
