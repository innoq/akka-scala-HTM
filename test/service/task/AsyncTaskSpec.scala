package service.task

import org.specs2.mutable.SpecificationLike
import akka.testkit.{ ImplicitSender, TestKit }
import akka.actor.ActorSystem
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import service.task.Task.Protocol.{ InvalidCommandRejected, TaskInitialized }
import org.specs2.time.NoTimeConversions

@RunWith(classOf[JUnitRunner])
class AsyncTaskSpec extends TestKit(ActorSystem("test-system")) with SpecificationLike with NoTimeConversions with ImplicitSender {
  import scala.concurrent.duration._
  "A Task that is uninitialized" should {
    "reply with a TaskInitialized event to an Init command" in {
      val task = system.actorOf(Task.props())
      task ! Init("1", Map("foo" -> "bar"))
      expectMsg(100.millis, TaskInitialized(TaskModel("1", taskData = Map("foo" -> "bar"))))
      true
    }
    "reply with an InvalidCommandRejected event to any other command" in {
      val task = system.actorOf(Task.props())
      task ! Start
      expectMsg(100.millis, InvalidCommandRejected(Start, Created, ""))
      true
    }
  }

}
