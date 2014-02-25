package service.escalation

import akka.actor.{ ActorLogging, Props, Actor }
import service.escalation.EscalationService.Protocol.Escalate

class EscalationService extends Actor with ActorLogging {

  def receive = {
    case Escalate(id) => {
      log.info("escalate task: " + id)
    }
  }
}

object EscalationService {
  val actorName = "EscalationService"

  def actorPath = s"/user/$actorName"

  def props(): Props = Props[EscalationService]

  object Protocol {
    case class Escalate(id: String)
  }
}
