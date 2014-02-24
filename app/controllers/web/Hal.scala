package controllers.web

import play.api.libs.json.JsObject

object HalLinks {
  def links(links: HalLink*) = HalLinks(links.toVector)
}

case class HalDocument(links: HalLinks, document: JsObject, embedded: Vector[HalDocument] = Vector.empty)
case class HalLink(name: String, href: { def url: String }, templated: Boolean = false)
case class HalLinks(links: Vector[HalLink])
