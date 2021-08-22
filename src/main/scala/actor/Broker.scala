package actor

import akka.actor.{Actor, ActorLogging, Props}
import blockchain.Transaction

object Broker {
    /**
     * Broker Actor: manager of the transactions
     * responsibility:
     *      - addition of new transactions
     *      - retrieval of pending ones
     * The broker actor reacts to 3 kind of messages
     * */
    sealed trait BrokerMessage // to identify the messages of Broker Actor
    case class AddTransaction(transaction: Transaction) extends BrokerMessage // add new transaction the list of pending ones
    case object GetTransactions extends BrokerMessage // retrieve pending transactions
    case object Clear extends BrokerMessage // empties list
    
    val props: Props = Props(new Broker) // to initialize the actor when it'll be created
}

class Broker extends Actor with ActorLogging {
    
    import Broker._
    
    var pending: List[Transaction] = List()
    
    override def receive: Receive = {
        case AddTransaction(transaction) => {
            pending = transaction :: pending
            log.info(s"Added $transaction to pending Transaction")
        }
        case GetTransactions => {
            log.info(s"Getting pending transactions")
            sender() ! pending // ! is tell operator --> "send the message and dont wait for a response"
        }
        case Clear => {
            pending = List()
            log.info(s"Clear pending transaction list")
        }
    }
}
