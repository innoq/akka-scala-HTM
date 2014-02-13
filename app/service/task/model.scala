package service.task

case class TaskModel(
  id: String,
  state: TaskState,
  role: Option[String] = None,
  userId: Option[String] = None,
  delegatedUser: Option[String] = None)

case class FilteredTask(id: String, reason: String)