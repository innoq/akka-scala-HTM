package controllers.web

import play.api.libs.json._
import play.api.libs.json.JsObject
import play.api.libs.json.JsArray
import play.api.data.validation.ValidationError
import play.api.i18n.Messages
import play.api.http.Writeable
import play.api.mvc.Codec

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

  def halLinks(links: (String, { def url: String })*) = hal(JsObject(Nil), links.toVector)

  def hal[T](content: T, embedded: (String, Vector[HalDocument]), links: (String, { def url: String })*)(implicit cw: Writes[T]): HalDocument = {
    val (name, elems) = embedded
    hal(content, links.toVector, Vector(name -> elems))
  }

  def hal[T](content: T, links: Vector[(String, { def url: String })], embedded: Vector[(String, Vector[HalDocument])] = Vector.empty)(implicit cw: Writes[T]): HalDocument = {
    implicit val write = DomainSerializers.halDocumentWrites
    HalDocument(
      HalLinks(links.toVector.map { case (name, link) => HalLink(name, link) }),
      Json.toJson(content)(cw).as[JsObject],
      embedded)
  }

  implicit def halWriter(implicit code: Codec): Writeable[HalDocument] =
    Writeable(d => code.encode(Json.toJson(d)(DomainSerializers.halDocumentWrites).toString()), Some("application/hal+json"))

  def failure(e: Throwable) = Json.obj("error" -> e.getMessage)

}
