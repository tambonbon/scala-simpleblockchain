package server

import actor.Node
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.{ActorMaterializer, Materializer}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import server.NodeRoutes

object Server extends App with NodeRoutes{ // NodeRoutes is a trait that contains all the http routes to the various endpoint of the node
    
//    def main(args: Array[String]): Unit = {
        val address = if (args.length > 0) args(0) else "localhost"
        val port = if (args.length > 1) args(1).toInt else 8080
        
        implicit val system: ActorSystem = ActorSystem("scala-simpleblockchain") // where we store our ActorSystem..
        // .. every actor in this system will talk to others inside it
        
        implicit val materializer = ActorMaterializer() // updated Materialize
        // Akka HTTP requires ActorMaterializer, which relates to Akka Streams
        
        implicit val node: ActorRef = system.actorOf(Node.props("scala-simpleblockchainNode0"))
        
        lazy val routes: Route = statusRoutes ~ transactionRoutes ~ mineRoutes
        // the Node Actors are created along side with HTTP routes with the node, which are chained by ~
        
        Http().bindAndHandle(routes, address, port) // bind the routes we pass to it as argument
    
        println(s"Server online at http://$address:$port/")
        Await.result(system.whenTerminated, Duration.Inf)
//    }
    
}
