package server

import actor.Node.{AddTransaction, GetStatus, GetTransactions, Mine}
import akka.http.scaladsl.server.Directives.{pathEnd, pathPrefix}
import blockchain.{Chain, Transaction}
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import utils.JsonSupplement.{ChainJsonFormat, StringJsonFormat, TransactionJsonFormat, listFormat}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import utils.JsonSupplement._

trait NodeRoutes extends SprayJsonSupport { // SprayJson is like Jackson in Java
    
    implicit def system: ActorSystem
    
    def node: ActorRef
    
    implicit lazy val timeout = Timeout(5.seconds)
    
    /* The three routes will be chained in a single one after their initialization in the server.*/
    lazy val statusRoutes: Route = pathPrefix("status") { // endpoint to ask for status
        concat(
            pathEnd {
                concat(
                    get {
                        val statusFuture: Future[Chain] = (node ? GetStatus).mapTo[Chain]
                        onSuccess(statusFuture) { status =>
                            complete(StatusCodes.OK, status)
                        }
                    }
                )
            }
        )
    }
    
        lazy val transactionRoutes: Route = pathPrefix("transactions") { // handle everything relating to transaction
            concat(
                pathEnd {
                    concat(
                        get {
                            val transactionsRetrieved: Future[List[Transaction]] =
                                (node ? GetTransactions).mapTo[List[Transaction]]
                            onSuccess(transactionsRetrieved) { transactions =>
                                complete(transactions.toList)
                            }
                        },
                        post {
                            entity(as[Transaction]) { transaction => // used to deserialize the JSON body into a Transaction object
                                val transactionCreated: Future[Int] =
                                    (node ? AddTransaction(transaction)).mapTo[Int]
                                onSuccess(transactionCreated) { done =>
                                    complete((StatusCodes.Created, done.toString))
                                }
                            }
                        }
                    )
                }
            )
        }
    
        lazy val mineRoutes: Route = pathPrefix("mine") { // start mining process
            concat(
                pathEnd {
                    concat(
                        get {
                            node ! Mine
                            complete(StatusCodes.OK)
                        }
                    )
                }
            )
        }
    
    
}
