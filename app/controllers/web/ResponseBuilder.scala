package controllers.web

import play.api.libs.json._
import play.api.libs.json.JsObject
import play.api.libs.json.JsArray
import play.api.data.validation.ValidationError
import play.api.i18n.Messages

trait ResponseBuilder {

  def error(msg: String) = Json.obj("msg" -> msg)

  type JsonErrors = Seq[(JsPath, Seq[ValidationError])]

  /**
   * { validation : {
   *     "/abc" : ["msg1", "msg2]
   *   }
   * }
   */
  def error(errors: JsonErrors) = {
    val errorFields = errors.map {
      case (path, vErrors) =>
        path.toString() -> JsArray(vErrors.map(error =>
          JsString(Messages.apply(error.message, error.args))))
    }
    Json.obj("validation" -> JsObject(errorFields))
  }

  def halDocument[T](content: T, links: (String, { def url: String })*)(implicit cw: Writes[T]) = {
    Json.toJson(HalDocument(
      HalLinks(links.toVector.map { case (name, link) => HalLink(name, link) }),
      Json.toJson(content)(cw).as[JsObject]))(DomainSerializers.halDocumentWrites)
  }

  def failure(e: Exception) = Json.obj("error" -> e.getMessage)

}
