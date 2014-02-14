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
      goto(Ready) using InitialData(taskModel) replying publishing(TaskInitialized(taskModel))
    }
  }

  when(Ready) {
    case Event(Claim(userId), InitialData(taskModel)) => {
      val model = taskModel.copy(userId = Some(userId))
      goto(Reserved) using ClaimedData(model) replying publishing(TaskClaimed(model))
    }
  }

  when(Reserved) {
    case Event(Start, data: ClaimedData) =>
      goto(InProgress) replying publishing(TaskStarted(data.taskData))
    case Event(Release, ClaimedData(model)) =>
      goto(Ready) using InitialData(model) replying publishing(TaskReleased(model))
  }

  when(InProgress) {
    case Event(Complete(result), ClaimedData(taskData)) =>
      goto(Completed) using CompletedData(taskData) replying publishing(TaskCompleted(taskData))
    case Event(Stop, ClaimedData(taskData)) =>
      goto(Reserved) replying publishing(TaskStopped(taskData))
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
    sealed abstract class TaskEvent {
      def taskModel: TaskModel
    }
    case class TaskInitialized(taskModel: TaskModel) extends TaskEvent
    case class TaskClaimed(taskModel: TaskModel) extends TaskEvent
    case class TaskStarted(taskModel: TaskModel) extends TaskEvent
    case class TaskReleased(taskModel: TaskModel) extends TaskEvent
    case class TaskCompleted(taskModel: TaskModel) extends TaskEvent
    case class TaskStopped(taskModel: TaskModel) extends TaskEvent
    case class TaskSkipped(taskId: String) extends TaskEvent {
      def taskModel = TaskModel.default(taskId, Map.empty)
    }
    case class InvalidCommandRejected(cmd: Command, stateName: TaskState, taskId: String) extends TaskEvent {
      def taskModel = TaskModel.default(taskId, Map.empty)
    }
  }
}
