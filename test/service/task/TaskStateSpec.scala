package service.task

import akka.actor.ActorSystem
import akka.testkit.TestActorRef
import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._

import play.api.test._
import play.api.test.Helpers._
import service.task._

/**
 * add your integration spec here.
 * An integration test will fire up a whole play application in a real (or headless) browser
 */
@RunWith(classOf[JUnitRunner])
class TaskStateSpec extends Specification {
  implicit val actorSystem = ActorSystem("Test")

  "A uninitialized task" should {
    "accept an init message which changes the state" in {
      val actorRef = TestActorRef[Task]
      actorRef ! Init("1", Map("test" -> "1"))
      actorRef.underlyingActor.stateData === InitialData("1", Map("test" -> "1"))
      actorRef.underlyingActor.stateName === Ready
    }
  }
  "A created task" should {
    "accept a claim event which assings the task to a user" in {
      val actorRef = TestActorRef[Task]
      actorRef ! Init("1", Map("test" -> "1"))
      actorRef ! Claim("tobias")
      actorRef.underlyingActor.stateData === ClaimedData("1", Map("test" -> "1"), "tobias")
      actorRef.underlyingActor.stateName === Reserved
    }
  }
  "A reserved task" should {
    "can be skipped" in {
      val actorRef = TestActorRef[Task]
      actorRef ! Init("1", Map("test" -> "1"))
      actorRef ! Claim("tobias")
      actorRef ! Skip
      actorRef.underlyingActor.stateData === EmptyData("1")
      actorRef.underlyingActor.stateName === Obsolete
    }
  }
}
