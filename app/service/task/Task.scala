package service.task

import akka.actor.{ FSM, Actor }

class Task extends Actor with FSM[TaskState, Data] {

  startWith(Created, Uninitialized)

  when(Created) {
    case Event(Init(input), data) => goto(Ready) using InitialData(input)
  }

  when(Ready) {
    case Event(Claim(userId), InitialData(input)) =>
      goto(Reserved) using Claimed(input, userId)
    case Event(Skip, _) =>
      goto(Obsolete)
  }

  initialize()

}
