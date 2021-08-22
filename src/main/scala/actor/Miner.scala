package actor

import actor.Miner.Validate
import akka.actor.FSM.Failure
import akka.actor.Status.Success
import akka.actor.{Actor, ActorLogging, Props}
import pow.ProofOfWork

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
    
    override def receive: Receive = ???
    
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
}
