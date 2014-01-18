package plugin

import play.api.Plugin
import akka.actor.ActorRef
import org.eligosource.eventsourced.journal.inmem.InmemJournalProps
import org.eligosource.eventsourced.core.{ EventsourcingExtension, Journal }
import play.api.libs.concurrent.Akka
import play.api.Application

class EventsourcedPlugin(app: Application) extends Plugin {

  var eventsourcedExtension: Option[EventsourcingExtension] = None

  override def onStart() {
    implicit val system = Akka.system(app)
    val journal: ActorRef = Journal(InmemJournalProps())
    val extension: EventsourcingExtension = EventsourcingExtension(system, journal)
    eventsourcedExtension = Some(extension)
  }

}
