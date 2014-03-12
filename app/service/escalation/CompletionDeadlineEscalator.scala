package service.escalation

import akka.actor._
import service.task._
import akka.actor.FSM.Transition

class CompletionDeadlineEscalator extends BaseEscalator {

  def transition(data: EscalationData): Receive = {
    case Transition(actorRef, oldState, newState: TaskState) => {
      if (newState.isFinalState) {
        log.debug(s"cancel deadline scheduler for task ${data.id}. Task is in final state $newState")
        data.scheduledEscalation.cancel()
        context.stop(self)
      } else stayWithNewState(data, newState)
    }
  }
}

object CompletionDeadlineEscalator {
  def props: Props = Props[CompletionDeadlineEscalator]
}
