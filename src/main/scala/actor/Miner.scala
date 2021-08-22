package actor

import actor.Miner.{Mine, Ready, Validate}
import akka.actor.FSM.Failure
import akka.actor.Status.Success
import akka.actor.{Actor, ActorLogging, Props}
import exception.{InvalidProofException, MinerBusyException}
import pow.ProofOfWork

import scala.concurrent.Future

object Miner {
    
    /**
     * Miner Actor: mining new blocks for our blockchain
     *
     * As we dont want to mine new blocks when we're mining another, the actor has 2 states:
     *  - ready: when it's ready to mine a new block
     *  - busy: when it's mining a block*/
    
    sealed trait MinerMessage
    case class Validate(hash: String, proof: Long) extends MinerMessage // ask for a validation of a proof..
    // .. and pass to Miner the hash and proof to check
    // Since this component is the one interacting PoW algorithm..
    // .. it is its duty to execute this check
    case class Mine(hash: String) extends MinerMessage // ask for mining starting from a hash
    case object Ready extends MinerMessage // triggers a state transition
    
    val props: Props = Props(new Miner)
}

class Miner extends Actor with ActorLogging {
    import context._
    
    override def receive: Receive = {
        case Ready => become(ready) // state transition
    }
    
    def validate: Receive = {
        case Validate(hash, proof) => {
            log.info(s"Validating proof $proof")
            if (ProofOfWork.validProof(hash, proof)) {
                log.info("proof is valid!")
                sender() ! Success
            }
            else {
                log.info("proof is not valid")
                sender() ! Failure(new InvalidProofException(hash, proof))
            }
        }
    }
    
    def ready: Receive = validate orElse {
        case Mine(hash) => {
            log.info(s"Mining hash $hash")
            val proof = Future {
                ProofOfWork.proofOfWork(hash)
            }
            sender() ! proof
            become(busy) // state transition
        }
        case Ready => {
            log.info("I'm ready to mine!")
            sender() ! Success("OK")
        }
    }
    
    def busy: Receive = validate orElse {
        case Mine(_) => {
            log.info("I'm busy mining")
            sender() ! Failure(new MinerBusyException("Miner is busy") )
        }
        case Ready => {
            log.info("I'm ready to mine new block")
            become(ready)
        }
    }
}

/**
 * 1. State transition is triggered using `become` function..
 * .. taking a function as argument
 * .. returning a Receive object
 * 2. When a mining request is received by Miner..
 * .. it responds with a Future containing the execution of PoW algo
 * ----> we can work asynchronously
 * 3. The **supervisor** of this Actor controls state transition */
