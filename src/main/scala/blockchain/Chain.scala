package blockchain

import crypto.Crypto
import spray.json
import spray.json.enrichAny
import utils.JsonSupplement.ChainLinkJsonFormat

import java.security.InvalidParameterException
import Chain.ChainLink

sealed trait Chain {
    /**
     * The chain is the core of our blockchain:
     * it is a linked list of blocks containing transactions
     */
    val index: Int
    val hash: String
    val values: List[Transaction]
    val proof: Long
    val timestamp: Long
    
    def ::(link: Chain): Chain = link match {
        case l: ChainLink => ChainLink(l.index, l.proof, l.values, this.hash, this.timestamp)
        case _ => throw new InvalidParameterException("Cannot add invalid link to chain")
    }
}

object Chain {
    /**
     * The chain can have 2 types: EmptyChain or ChainLink
     * - EmptyChain:
     * - block zero (genesis block)
     * - singleton
     * - ChainLink:
     * - regular mined block
     */
    
    case object EmptyChain extends Chain {
        override val index: Int = 0 //current height of the blockchain
        override val hash: String = "1" // set default hash for empty chain, while in ChainLink is computed converting object to its json
        override val values: List[Transaction] = Nil // list of transaction
        override val proof: Long = 100L // proof that validated block
        override val timestamp: Long = System.currentTimeMillis()
    }
    
    case class ChainLink(index: Int, proof: Long, values: List[Transaction], previousHash: String = "",
                          timestamp: Long = System.currentTimeMillis(), tail: Chain = EmptyChain) extends Chain {
        val hash = Crypto.sha256Hash(json.enrichAny(this).toJson.toString)
    }
    
    def apply[T](b: Chain*): Chain = {
        if (b.isEmpty) EmptyChain
        else {
            val link = b.head.asInstanceOf[ChainLink]
            ChainLink(link.index, link.proof, link.values, link.previousHash, link.timestamp, apply(b.tail: _*))
            // we create a new ChainLink, adding as a tail the result of apply method on remaining blocks of the list
            // this way, the list of blocks is added following order of list
        }
    }
}
