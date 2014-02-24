package controllers.web

import scala._
import play.api.libs.json.JsObject
import play.api.mvc.Accepting

object Hal {
  val accept = Accepting("application/hal+json")
}

case class HalDocument(links: HalLinks, document: JsObject, embedded: Vector[(String, Vector[HalDocument])] = Vector.empty)
case class HalLink(name: String, href: { def url: String }, templated: Boolean = false)
case class HalLinks(links: Vector[HalLink])
