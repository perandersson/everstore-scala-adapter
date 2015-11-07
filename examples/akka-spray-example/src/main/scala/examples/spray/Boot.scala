package examples.spray

import akka.actor.ActorSystem
import akka.actor.Props
import akka.io.IO
import com.typesafe.config.ConfigFactory
import examples.spray.routing.RestRouting
import spray.can.Http

object Boot extends App {
  // Initialize the akka actor system
  implicit val system = ActorSystem("example")

  // Load application.conf
  val config = ConfigFactory.load()

  // REST entry-point actor for all requests
  val serviceActor = system.actorOf(Props(new RestRouting()), name = "example-rest")

  // Register function to be called when application is shutting down
  system.registerOnTermination {
    system.log.info("Shutting down backend")
  }

  // Bind actor to HTTP framework
  IO(Http) ! Http.Bind(serviceActor, interface = config.getString("http.host"), port = config.getInt("http.port"))
}
