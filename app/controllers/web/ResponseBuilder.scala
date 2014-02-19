package controllers.web

import play.api.libs.json.Json

trait ResponseBuilder {

  def error(msg: String) = Json.obj("msg" -> msg)

  def failure(e: Exception) = Json.obj("error" -> e.getMessage)

}
