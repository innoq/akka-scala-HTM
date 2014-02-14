package controllers

import play.api._
import play.api.mvc._
import plugin.EventsourcedPlugin
import play.api.libs.json.Json

object Application extends Controller {

  def index = Action { implicit request =>
    render {
      case Accepts.Html() => html.withHeaders("Access-Control-Allow-Origin" -> "*")
      case Accepts.Json() => json.withHeaders("Access-Control-Allow-Origin" -> "*")
    }
  }

  def html = {
    val a = play.api.Play.current.plugin[EventsourcedPlugin] match {
      case Some(plugin) => plugin.eventsourcedExtension
      case None => throw new Exception("No eventsourced plugin registered")
    }
    Ok(views.html.index())
  }

  def json(implicit request: Request[_]) = {
    val url = routes.Tasks.createTaskAction.absoluteURL()
    val json = Json.toJson(
      Map("resources" ->
        Map("tasks" ->
          Map("href" -> url))))
    Ok(json)
  }
}