package service.task

trait TaskModel {
  def id: String
  def role: Option[String]
  def userId: Option[String]
  def delegatedUser: Option[String]
  def taskData: TaskData
}

case class TaskModelImpl(
  id: String,
  role: Option[String] = None,
  userId: Option[String] = None,
  delegatedUser: Option[String] = None,
  taskData: TaskData = Map.empty) extends TaskModel

class TaskView(taskModel: TaskModel, val taskState: TaskState) extends TaskModel {
  def id = taskModel.id
  def role = taskModel.role
  def userId = taskModel.userId
  def taskData = taskModel.taskData
  def delegatedUser = taskModel.delegatedUser
}

case class FilteredTask(id: String, reason: String)