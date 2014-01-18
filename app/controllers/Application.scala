package controllers

import play.api._
import play.api.mvc._
import plugin.EventsourcedPlugin

object Application extends Controller {

  def index = Action {
    val a = play.api.Play.current.plugin[EventsourcedPlugin] match {
      case Some(plugin) => plugin.eventsourcedExtension
      case None => throw new Exception("No eventsourced plugin registered")
    }
    Ok(views.html.index())
  }

}