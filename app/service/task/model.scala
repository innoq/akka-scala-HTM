package service.task

trait TaskModel {
  def id: String
  def taskType: String
  def role: Option[String]
  def userId: Option[String]
  def delegatedUser: Option[String]
  def taskData: TaskData
  def result: TaskData
}

object TaskModel {
  def apply(id: String,
    taskType: String,
    role: Option[String] = None,
    userId: Option[String] = None,
    delegatedUser: Option[String] = None,
    taskData: TaskData = EmptyTaskData,
    result: TaskData = EmptyTaskData) = TaskModelImpl(id, taskType, role, userId, delegatedUser, taskData, result)
  def unapply(taskModel: TaskModel) = Some((taskModel.id, taskModel.taskType, taskModel.role, taskModel.userId,
    taskModel.delegatedUser, taskModel.taskData, taskModel.result))
  def withUser(id: String, user: String, taskData: TaskData) = apply(id, "generic", None, Some(user), None, taskData)
  def default(id: String) = withData(id, EmptyTaskData)
  def withData(id: String, taskData: TaskData) = apply(id, "generic", None, None, None, taskData)
}

case class TaskModelImpl(
  id: String,
  taskType: String,
  role: Option[String],
  userId: Option[String],
  delegatedUser: Option[String],
  taskData: TaskData,
  result: TaskData) extends TaskModel

class TaskView(val taskModel: TaskModel, val taskState: TaskState) extends TaskModel {
  def id = taskModel.id
  def taskType = taskModel.taskType
  def role = taskModel.role
  def userId = taskModel.userId
  def taskData = taskModel.taskData
  def result = taskModel.result
  def delegatedUser = taskModel.delegatedUser
}

case class FilteredTask(id: String, reason: String)