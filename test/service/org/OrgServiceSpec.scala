package service.org

import org.specs2.mutable.SpecificationLike
import akka.testkit.{ ImplicitSender, TestKit }
import akka.actor.ActorSystem
import service.org.OrgService.Protocol.{ FilteredTasks, FilterTasks }
import service.task._
import concurrent.duration._
import org.specs2.time.NoTimeConversions
import service.task.FilteredTask
import service.org.OrgService.Protocol.FilteredTasks
import service.org.OrgService.Protocol.FilterTasks
import scala.Some

class OrgServiceSpec extends TestKit(ActorSystem("test-system")) with SpecificationLike with ImplicitSender with NoTimeConversions {

  "The OrgService" should {
    "return a filtered list of tasks" in {
      val orgService = system.actorOf(OrgService.props())
      val tasks = Vector(new TaskView(
        new TaskModelImpl("1", role = Some("management")), Ready))
      orgService ! FilterTasks("1", tasks)
      expectMsgPF(3000.millis) {
        case FilteredTasks(userId, Vector(FilteredTask("1", "in role"))) =>
          true
      }
      true
    }
  }

}
