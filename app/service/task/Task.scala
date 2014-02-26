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
      goto(Ready) using ProcessData(taskModel) replying publishing(TaskInitialized(Ready, taskModel))
    }
  }

  when(Ready) {
    case Event(Claim(userId), ProcessData(taskModel)) => {
      val model = taskModel.copy(userId = Some(userId))
      goto(Reserved) using ProcessData(model) replying publishing(TaskClaimed(Reserved, model))
    }
    case Event(ReClaim(userId), ProcessData(taskModel)) => {
      val model = taskModel.copy(userId = Some(userId))
      goto(Reserved) using ProcessData(model) replying publishing(TaskClaimed(Reserved, model))
    }
  }

  when(Reserved) {
    case Event(Start, data: ProcessData) =>
      goto(InProgress) replying publishing(TaskStarted(InProgress, data.taskData))
    case Event(Release, ProcessData(model)) =>
      goto(Ready) using ProcessData(model) replying publishing(TaskReleased(Ready, model))
    case Event(ReClaim(userId), ProcessData(model)) => {
      val uModel = model.copy(userId = Some(userId))
      stay using ProcessData(uModel) replying publishing(TaskReClaimed(Reserved, uModel))
    }
  }

  when(InProgress) {
    case Event(Complete(result), ProcessData(taskData)) => {
      val newTaskData = taskData.copy(result = result)
      goto(Completed) using ProcessData(newTaskData) replying publishing(TaskCompleted(Completed, newTaskData))
    }
    case Event(Stop, ProcessData(taskData)) =>
      goto(Reserved) replying publishing(TaskStopped(Reserved, taskData))
    case Event(ReClaim(userId), ProcessData(taskData)) => {
      val newTaskData = taskData.copy(userId = Some(userId))
      goto(Reserved) using ProcessData(newTaskData) replying publishing(TaskReClaimed(Reserved, newTaskData))
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
