package actor

import actor.Blockchain._
import akka.actor.{ActorLogging, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}
import blockchain.Chain.ChainLink
import blockchain.{Chain, Transaction}

object Blockchain {
    
    /**
     * Blockchain Actor interacts with business logic of the blockchain
     *
     * It can add a new block to blockchain & can retrieve info about the state of blockchain
     * ---> It can also PERSIST & RECOVER the state of blockchain <---
     * */
    sealed trait BlockchainEvent // handle events that will trigger persistence logic
    case class AddBlockEvent(transactions: List[Transaction], proof: Long) extends BlockchainEvent // the event that will update our state
    
    sealed trait BlockchainCommand // send direct commands to actor
    case class AddBlockCommand(transaction: List[Transaction], proof: Long) extends BlockchainCommand
    case object GetChain extends BlockchainCommand
    case object GetLastHash extends BlockchainCommand
    case object GetLastIndex extends BlockchainCommand
    
    case class State(chain: Chain) // where we store the state of our blockchain..
    // the idea is to update the state every time a new block is created
    
    def props(chain: Chain, nodeID: String): Props = Props(new Blockchain(chain, nodeID))
    
}

class Blockchain(chain: Chain, nodeID: String) extends PersistentActor with ActorLogging {
    // nodeID is part of the persistenceID that we override
    
    var state = State(chain)
    
    def updateState(evt: BlockchainEvent) = evt match {
        case AddBlockEvent(transactions, proof) =>
            {
                state = State(ChainLink(state.chain.index + 1, proof, transactions) :: state.chain)
                log.info(s"Added blocl ${state.chain.index} containing ${transactions.size} transactions")
            }
    }
    
    override def receiveRecover: Receive = { // reacts to the recovery messages sent by the persistence logic
        case SnapshotOffer(metadata, snapshot: State) => { // snapshot is a persisted state
            // SnapshotOffer is a message in akka persistence..
            // .. which represents a persisted state, and the current state becomes the one provided
            log.info(s"Recovering from snapshot ${metadata.sequenceNr} at block ${snapshot.chain.index}")
            state = snapshot
        }
        case RecoveryCompleted => log.info("Recovery completed") // RecoveryCompleted message informs us ..
            //.. that the recovery process completed successfully
        case evt: AddBlockEvent => updateState(evt) // AddBlockEvent triggers the updateSate function
    }
    
    
    override def receiveCommand: Receive = { // used to react the direct commands sent to actor
        case SaveSnapshotSuccess(metadata) => log.info(s"Snapshot ${metadata.sequenceNr} saved successfully") // keep track
        case SaveSnapshotFailure(metadata, reason) => log.error(s"Error saving snapshot ${metadata.sequenceNr}: ${reason.getMessage}") // keep track
        case AddBlockCommand(transactions : List[Transaction], proof: Long) => { // creates and fires an AddBlock event ..
            // .. that is persisted in the event journal of the Actor
            // ---> this way, the events can be played in case of recovery
            persist(AddBlockEvent(transactions, proof)) {event =>
                updateState(event)
            }
        
            // This is a workaround to wait until the state is persisted
            deferAsync(Nil) { _ => // waits until the state is updated after the processing of the event ..
                // .. once the event has been executed, the actor can save the snapshot of the state..
                // .. and inform the sender of the message with the updated last index of the Chain
                saveSnapshot(state)
                sender() ! state.chain.index
            }
        }
        case AddBlockCommand(_, _) => log.error("invalid add block command")
        case GetChain => sender() ! state.chain
        case GetLastHash => sender() ! state.chain.hash
        case GetLastIndex => sender() ! state.chain.index
    }
    
    override def persistenceId: String = s"chainer-$nodeID"
}
