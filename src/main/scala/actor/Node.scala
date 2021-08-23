package actor

import actor.Blockchain.{AddBlockCommand, GetChain, GetLastHash, GetLastIndex}
import actor.Broker.Clear
import actor.Miner.{Ready, Validate}
import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.ask
import akka.util.Timeout
import blockchain.Chain.EmptyChain
import blockchain.Transaction

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object Node {
    
    /**
     * Node actor is the BACKBONE of our blockchain node
     *
     * It is the *supervisor* of all the other actors, plus the one communicating with the outside world through REST API
     * */
    
    sealed trait NodeMessage
    
    // the Node Actor has to handle all the high level messages coming from the REST API..
    // ---> that's why we have in general the same messages implemented in the children actors
    case class AddTransaction(transaction: Transaction) extends NodeMessage
    
    case class TransactionMessage(transaction: Transaction, nodeID: String) extends NodeMessage
    
    case class CheckPowSolution(solution: Long) extends NodeMessage
    
    case class AddBlock(proof: Long) extends NodeMessage
    
    case object GetTransactions extends NodeMessage
    
    case object Mine extends NodeMessage
    
    case object StopMining extends NodeMessage
    
    case object GetStatus extends NodeMessage
    
    case object GetLastBlockIndex extends NodeMessage
    
    case object GetLastBlockHash extends NodeMessage
    
    def props(nodeID: String): Props = Props(new Node(nodeID))
    
    def createCoinbaseTransaction(nodeId: String) = Transaction("coinbase", nodeId, 100)
    // create a transaction assigning a predefined coin to the node itself..
    // .. this will be the REWARD for the successful mining of a new block of the blockchain
    
}

class Node(nodeID: String) extends Actor with ActorLogging {
    import Node._
    
    implicit lazy val timeout = Timeout(5.seconds)
    
    val broker = context.actorOf(Broker.props)
    val miner  = context.actorOf(Miner.props)
    val blockchain = context.actorOf(Blockchain.props(EmptyChain, nodeID))
    // the blockchain actor is initialized with the EmptyChain and the nodeID of the Node
    
    miner ! Ready
    // once everything is created, we inform the Miner Actor to be ready to mine sending it a Ready message
    
    def waitForSolution(solution: Future[Long]) = Future {
        solution onComplete {
            case Success(proof) => {
                broker ! Broker.AddTransaction(createCoinbaseTransaction(nodeID))
                self ! AddBlock(proof)
                miner ! Ready
            }
            case Failure(e) => log.error(s"Error finding PoW solution: ${e.getMessage}")
        }
    }
    
    override def receive: Receive = {
        case TransactionMessage(transaction, messageNodeID) => {
            log.info(s"Received transaction message from $messageNodeID")
            if (messageNodeID != nodeID) broker ! Broker.AddTransaction(transaction)
        }
        case AddTransaction(transaction) => { // AddTransaction message triggers the logic to store a new transaction ..
            // .. in the list of pending ones of our blockchain
            // Aim: The Node Actor responds with the `index` of the block containing the transaction
            val node = sender() // store address of `sender()` in a `node` value to use it later
            (blockchain ? GetLastIndex).mapTo[Int] onComplete { // we `ask` to the Blockchain Actor the last index of the chain..
                        // .. after we send to the Broker Actor a message to add a new transaction (hence the `case AddTransaction`)
                        // Remarks: `ask` operator (?) ---> send a message to an actor and WAIT for a response
                        // The response (mapped to an Int) can be a Success or a Failure
                case Success(index) =>
                    broker ! Broker.AddTransaction(transaction)
                    node ! (index + 1) // we send back to the sender (node) the `index+1` ..
                    // .. since it is the index of the next mined block
                case Failure(exception) => node ! akka.actor.Status.Failure(exception)
                    /* Remarks: the pattern here is:
                    * ask --> wait for a response --> handle success/failure
                    * */
            }
        }
        case CheckPowSolution(solution) => { // We have to check if a solution to PoW algo is correct or not!
            val node = sender()
            (blockchain ? GetLastHash).mapTo[String] onComplete { // we ask to the Blockchain Actor the last hash of the chain (in order to validate)
                case Success(hash: String) => miner ! (Validate(hash, proof = solution), node) // ? what is tell? (can we use ! instead)
                case Failure(exception) => node ! akka.actor.Status.Failure(exception)
            }
        }
        case AddBlock(proof) => { // other nodes can mine blocks, so we may receive a request to add a block that we didn't mine (?wtf this means?)
            val node = sender()
            (self ? CheckPowSolution(proof)) onComplete {
                case Success(_) => {
                    (broker ? Broker.GetTransactions).mapTo[List[Transaction]] onComplete {
                        case Success(transactions) => blockchain.tell(AddBlockCommand(transactions, proof), node)
                        case Failure(e) => node ! akka.actor.Status.Failure(e)
                    }
                    broker ! Clear
                }
                case Failure(e) => node ! akka.actor.Status.Failure(e)
            }
        }
        case Mine => {
            val node = sender()
            (blockchain ? GetLastHash).mapTo[String] onComplete {
                case Success(hash) => (miner ? Miner.Mine(hash)).mapTo[Future[Long]] onComplete {
                    case Success(solution) => waitForSolution(solution)
                    case Failure(e) => log.error(s"Error finding PoW solution: ${e.getMessage}")
                }
                case Failure(e) => node ! akka.actor.Status.Failure(e)
            }
        }
        case GetTransactions => broker forward(Broker.GetTransactions)
        case GetStatus => blockchain forward(GetChain)
        case GetLastBlockIndex => blockchain forward(GetLastIndex)
        case GetLastHash => blockchain forward(GetLastHash)
             /* the `forward` operator: sender [who?] of the message will be the one that originated that message ...
             * .. not the Node Actor
             * */
    }
}
