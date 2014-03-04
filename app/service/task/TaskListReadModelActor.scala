package service.task

import akka.actor.{ ActorRef, ActorLogging, Props, Actor }
import akka.pattern._
import service.task.Task.Protocol.{ InvalidCommandRejected, TaskEvent, TaskInitialized }
import scala.concurrent.Future
import service.task.TaskListReadModelActor.Protocol._
import service.org.OrgService.Protocol.OrgServiceUnreachable
import service.org.OrgService.Protocol.FilteredTasks
import service.task.TaskListReadModelActor.Protocol.TaskList
import service.org.OrgService.Protocol.FilterTasks
import service.task.TaskListReadModelActor.Protocol.GetTaskList
import scala.Some
import service.task.TaskListReadModelActor.Protocol.GetTask
import controllers.web.Defaults

class TaskListReadModelActor(val orgServer: ActorRef) extends Actor with Defaults with ActorLogging {

  implicit def dis = this.context.dispatcher

  this.context.system.eventStream.subscribe(self, classOf[TaskEvent])

  var model = Map.empty[String, TaskLinkVersionView]

  def receive = {
    case i: InvalidCommandRejected => {
      //don't update task model, continue
    }
    case s: TaskEvent => {
      val view = model.get(s.taskModel.id).map(_.update(s.taskModel, s.state, s.links))
        .getOrElse(new TaskLinkVersionView(s.taskModel, s.state, s.links, 1))
      model = model + (s.taskModel.id -> view)
    }
    case GetTaskList(userId) => {
      val taskViews = model.values.toVector
      val filteredTaskViews: Future[Msg] = userId match {
        case None => Future.successful(TaskList(taskViews))
        case Some(userId) => {
          ask(orgServer, FilterTasks(userId, taskViews)).map {
            case FilteredTasks(_, tasks) => TaskList(tasks.map { case FilteredTask(id, _) => model(id) })
            case OrgServiceUnreachable => TaskListUnavailable
          }
        }
      }
      filteredTaskViews pipeTo sender
    }
    case GetTask(id) => model.get(id) match {
      case Some(view) => sender ! TaskList(Seq(view))
      case None => sender ! NotFound(id)
    }
  }
}

object TaskListReadModelActor {

  val actorName = "TaskListReadModelManager"

  def actorPath = s"/user/$actorName"

  def props(orgServer: ActorRef) = Props(classOf[TaskListReadModelActor], orgServer)

  object Protocol {
    sealed trait Msg
    case class GetTaskList(userId: Option[String]) extends Msg
    case class GetTask(taskId: String) extends Msg
    case class TaskList(elems: Seq[TaskLinkVersionView]) extends Msg
    case class NotFound(taskId: String) extends Msg
    case object TaskListUnavailable extends Msg
  }
}
