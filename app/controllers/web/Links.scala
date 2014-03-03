package controllers.web

import controllers.routes

object Link {
  def unapply(link: Link) = Some(link.name, link.link)
}

sealed abstract class Link {
  def link: String
  def name: String
}

case class Assignee(id: String) extends Link {
  def link = routes.TaskFlow.changeAssignee(id).url
  val name = "assignee"
}

case class Skip(id: String) extends Link {
  def link = routes.TaskFlow.skip(id).url
  def name = "skip"
}

case class Start(id: String) extends Link {
  def link = routes.TaskFlow.start(id).url
  def name = "start"
}

case class Stop(id: String) extends Link {
  def link = routes.TaskFlow.stop(id).url
  def name = "stop"
}

case class Output(id: String) extends Link {
  def link = routes.TaskFlow.output(id).url
  def name = "output"
}

case object TaskList extends Link {
  def link = routes.TaskView.list(None).url
  def name = "list"
}

object Self {
  def apply(url: String) = new Self {
    val link: String = url
  }
}

sealed trait Self extends Link {
  def name = "self"
}

case class SelfTask(id: String) extends Self {
  def link = routes.TaskView.lookup(id).url
}
