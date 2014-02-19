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
    case Event(Init(taskId, taskType, input, role, userId, delegate), data) => {
      val taskModel = TaskModelImpl(taskId, taskType, role, userId, delegate, input)
      goto(Ready) using InitialData(taskModel) replying publishing(TaskInitialized(Ready, taskModel))
    }
  }

  when(Ready) {
    case Event(Claim(userId), InitialData(taskModel)) => {
      val model = taskModel.copy(userId = Some(userId))
      goto(Reserved) using ClaimedData(model) replying publishing(TaskClaimed(Reserved, model))
    }
  }

  when(Reserved) {
    case Event(Start, data: ClaimedData) =>
      goto(InProgress) replying publishing(TaskStarted(InProgress, data.taskData))
    case Event(Release, ClaimedData(model)) =>
      goto(Ready) using InitialData(model) replying publishing(TaskReleased(Ready, model))
  }

  when(InProgress) {
    case Event(Complete(result), ClaimedData(taskData)) =>
      goto(Completed) using CompletedData(taskData) replying publishing(TaskCompleted(Completed, taskData))
    case Event(Stop, ClaimedData(taskData)) =>
      goto(Reserved) replying publishing(TaskStopped(Reserved, taskData))
  }

  when(Obsolete) {
    case _ => stay()
  }

  when(Completed) {
    case _ => stay()
  }

  whenUnhandled {
    case Event(Skip, d) =>
      goto(Obsolete) using EmptyData(d.taskId) replying TaskSkipped(Obsolete, d.taskId)
    case Event(cmd: Command, data) =>
      stay replying publishing(InvalidCommandRejected(cmd, stateName, data.taskId))
  }

  onTransition {
    case e => log.debug(s"task ${this.stateData.taskId} transition => $e")
  }

  initialize()

}

object Task {
  def props() = Props[Task]

  object Protocol {
    sealed abstract class TaskEvent {
      def taskModel: TaskModel
      def state: TaskState
    }
    case class TaskInitialized(state: TaskState, taskModel: TaskModel) extends TaskEvent
    case class TaskClaimed(state: TaskState, taskModel: TaskModel) extends TaskEvent
    case class TaskStarted(state: TaskState, taskModel: TaskModel) extends TaskEvent
    case class TaskReleased(state: TaskState, taskModel: TaskModel) extends TaskEvent
    case class TaskCompleted(state: TaskState, taskModel: TaskModel) extends TaskEvent
    case class TaskStopped(state: TaskState, taskModel: TaskModel) extends TaskEvent
    case class TaskSkipped(state: TaskState, taskId: String) extends TaskEvent {
      def taskModel = TaskModel.default(taskId, Map.empty)
    }
    case class InvalidCommandRejected(cmd: Command, state: TaskState, taskId: String) extends TaskEvent {
      def taskModel = TaskModel.default(taskId, Map.empty)
    }
  }
}
