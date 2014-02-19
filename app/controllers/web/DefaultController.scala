package controllers.web

import play.api.mvc.Controller

class DefaultController extends Controller with Defaults with DomainSerializers with ResponseBuilder
