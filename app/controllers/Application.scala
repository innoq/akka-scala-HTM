package controllers

import play.api._
import play.api.mvc._
import play.api.libs.json.Json
import controllers.web.{ Hal, DefaultController }

object Application extends DefaultController {

  val AcceptsJsonHome = Accepting("application/json-home")

  def index = Action { implicit request =>
    render {
      case Accepts.Html() => html
      case Hal.accept() => halIndex
      case AcceptsJsonHome() | Accepts.Json() => json
    }
  }

  def halIndex = {
    Ok(halLinks("self" -> routes.Application.index(), "tasks" -> routes.TaskFlow.createTaskAction())).as(Hal.accept.mimeType)
  }

  def html = {
    Ok(views.html.index())
  }

  def json(implicit request: Request[_]) = {
    val url = routes.TaskFlow.createTaskAction.absoluteURL()
    val json = Json.toJson(
      Map("resources" ->
        Map("tasks" ->
          Map("href" -> url))))
    Ok(json).as(AcceptsJsonHome.mimeType)
  }

  def apiBrowser = Action { request =>
    SeeOther("http://" + request.host + "/assets/browser/browser.html")
  }
}