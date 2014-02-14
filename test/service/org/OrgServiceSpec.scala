package service.org

import org.specs2.mutable.SpecificationLike
import akka.testkit.{ ImplicitSender, TestKit }
import akka.actor.ActorSystem
import service.org.OrgService.Protocol.{ FilteredTasks, FilterTasks }
import service.task.{ FilteredTask, Ready, TaskModel }
import concurrent.duration._
import org.specs2.time.NoTimeConversions

class OrgServiceSpec extends TestKit(ActorSystem("test-system")) with SpecificationLike with ImplicitSender with NoTimeConversions {

  "The OrgService" should {
    "return a filtered list of tasks" in {
      val orgService = system.actorOf(OrgService.props())
      val tasks = Vector(TaskModel("1", Ready, role = Some("management")))
      orgService ! FilterTasks("1", tasks)
      expectMsgPF(3000.millis) {
        case FilteredTasks(userId, Vector(FilteredTask("1", "in role"))) =>
          true
      }
      true
    }
  }

}
