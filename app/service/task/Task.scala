package service.task

import akka.actor.{ FSM, Actor }

class Task extends Actor with FSM[TaskState, Data] {

  startWith(Created, UninitializedData)

  when(Created) {
    case Event(Init(input), data) =>
      goto(Ready) using InitialData(input)
  }

  when(Ready) {
    case Event(Claim(userId), InitialData(input)) =>
      goto(Reserved) using ClaimedData(input, userId)
  }

  when(Reserved) {
    case Event(Start, data) =>
      goto(InProgress) using data
    case Event(Release, ClaimedData(input, _)) =>
      goto(Ready) using InitialData(input)
  }

  when(InProgress) {
    case Event(Complete(result), ClaimedData(input, userId)) =>
      goto(Completed) using CompletedData(input, userId, result)
    case Event(Stop, _) =>
      goto(Reserved)
  }

  whenUnhandled {
    case Event(Skip, _) =>
      goto(Obsolete) using EmptyData
  }

  initialize()

}
