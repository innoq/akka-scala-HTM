package service.escalation

import akka.actor.Props
import akka.actor.FSM.Transition
import service.task.{ Ready, TaskState }

class StartDeadlineEscalator extends BaseEscalator {

  def transition(data: EscalationData): Receive = {
    case Transition(actorRef, oldState, newState: TaskState) => {
      if (newState.isFinalState) {
        log.debug(s"cancel deadline scheduler for task ${data.id}. Task is in final $newState")
        data.scheduledEscalation.cancel()
        context.stop(self)
      } else if(newState.isClaimed) {
        log.debug(s"cancel deadline scheduler for task ${data.id}. Task is claimed in state $newState")
        data.scheduledEscalation.cancel()
      } else if (taskUnclaimed(newState)) {
        scheduleCompletionWatch(data.id, data.completionDeadline)
        stayWithNewState(data, newState)
      } else stayWithNewState(data, newState)
    }
  }

  def taskUnclaimed(newState: TaskState) = newState == Ready
}

object StartDeadlineEscalator {
  def props = Props[StartDeadlineEscalator]
}
