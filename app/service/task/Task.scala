package service.task

import service.task.Task.Protocol._
import service.task.Task.Protocol.TaskClaimed
import service.task.Task.Protocol.TaskInitialized
import akka.actor.{ ActorLogging, FSM, Actor, Props }
import service.task

class Task extends Actor with FSM[TaskState, Data] with ActorLogging {

  startWith(Created, UninitializedData(""))

  when(Created) {
    case Event(Init(taskId, taskType, startDeadline, compDeadline, input, role, userId, delegate), data) => {
      val taskModel = TaskModel(taskId, taskType, startDeadline, compDeadline, role, userId, delegate, input)
      goto(Ready) using InitialData(taskModel) replying publishing(TaskInitialized(Ready, taskModel))
    }
  }

  when(Ready) {
    case Event(Claim(userId), InitialData(taskModel)) => {
      val model = taskModel.copy(userId = Some(userId))
      goto(Reserved) using ClaimedData(model) replying publishing(TaskClaimed(Reserved, model))
    }
    case Event(ReClaim(userId), InitialData(taskModel)) => {
      val model = taskModel.copy(userId = Some(userId))
      goto(Reserved) using ClaimedData(model) replying publishing(TaskClaimed(Reserved, model))
    }
  }

  when(Reserved) {
    case Event(Start, data: ClaimedData) =>
      goto(InProgress) replying publishing(TaskStarted(InProgress, data.taskData))
    case Event(Release, ClaimedData(model)) =>
      goto(Ready) using InitialData(model) replying publishing(TaskReleased(Ready, model))
    case Event(ReClaim(userId), ClaimedData(model)) => {
      val uModel = model.copy(userId = Some(userId))
      stay using ClaimedData(uModel) replying publishing(TaskReClaimed(Reserved, uModel))
    }
  }

  when(InProgress) {
    case Event(Complete(result), ClaimedData(taskData)) => {
      val newTaskData = taskData.copy(result = result)
      goto(Completed) using CompletedData(newTaskData) replying publishing(TaskCompleted(Completed, newTaskData))
    }
    case Event(Stop, ClaimedData(taskData)) =>
      goto(Reserved) replying publishing(TaskStopped(Reserved, taskData))
    case Event(ReClaim(userId), ClaimedData(taskData)) => {
      val newTaskData = taskData.copy(userId = Some(userId))
      goto(Reserved) using ClaimedData(newTaskData) replying publishing(TaskReClaimed(Reserved, newTaskData))
    }
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
      stay using data replying publishing(InvalidCommandRejected(cmd, stateName, data.taskId))
  }

  onTransition {
    case (from, to) if to.isFinalState => {
      context.parent ! TaskDone(this.stateData.taskId, to)
    }
    case e => logTransition(e)
  }

  def logTransition(e: (task.TaskState, task.TaskState)) {
    log.debug(s"task ${this.stateData.taskId} transition => $e")
  }

  initialize()

  def publishing(state: TaskEvent) = {
    context.system.eventStream.publish(state)
    state
  }

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
    case class TaskReClaimed(state: TaskState, taskModel: TaskModel) extends TaskEvent
    case class TaskStarted(state: TaskState, taskModel: TaskModel) extends TaskEvent
    case class TaskReleased(state: TaskState, taskModel: TaskModel) extends TaskEvent
    case class TaskCompleted(state: TaskState, taskModel: TaskModel) extends TaskEvent
    case class TaskStopped(state: TaskState, taskModel: TaskModel) extends TaskEvent
    case class TaskSkipped(state: TaskState, taskId: String) extends TaskEvent {
      def taskModel = TaskModel.default(taskId)
    }
    case class InvalidCommandRejected(cmd: Command, state: TaskState, taskId: String) extends TaskEvent {
      def taskModel = TaskModel.default(taskId)
    }
    case class TaskDone(taskId: String, state: TaskState) extends TaskEvent {
      def taskModel = TaskModel.default(taskId)
    }
  }
}
