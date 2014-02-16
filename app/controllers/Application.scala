package controllers

import play.api._
import play.api.mvc._
import play.api.libs.json.Json
import controllers.web.DefaultController

object Application extends DefaultController {

  def index = Action { implicit request =>
    render {
      case Accepts.Html() => html
      case Accepts.Json() => json
    }
  }

  def html = {
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