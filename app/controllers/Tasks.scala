package controllers

import akka.pattern.ask
import play.api._
import play.api.Play.current
import play.api.mvc._
import play.api.libs.concurrent._
import play.api.libs.json._
import scala.concurrent.duration._
import service.task._
import scala.concurrent.ExecutionContext.Implicits.global

object Tasks extends Controller {

  def createTask = Action.async(parse.json) { request =>
    import Task.Protocol._
    val task = request.body \ "task"
    val input = task \ "input"
    val inputAsMap = input.as[JsObject].fields.map { case (key, value) => (key -> value.toString()) }.toMap
    val manager = Akka.system.actorSelection("/user/TaskManager")
    val result = ask(manager, CreateTask(inputAsMap))(2 seconds).mapTo[TaskInitialized]
    result.map { task => Ok(task.toString()) }
  }

}
