package pow

import crypto.Crypto
import spray.json.enrichAny
import utils.JsonSupplement.StringJsonFormat

import scala.annotation.tailrec

object ProofOfWork {
    
    /**
     * The PoW algorithm is fundamental for mining of new blocks
     * 1.Take the hash of the last block and a number representing the proof.
     * 2. Concatenate the hash and the proof in a string.
     * 3. hash the resulting string using the SHA-256 algorithm.
     * 4. check the 4 leading characters of the hash: if they are four zeros return the proof.
     * 5. otherwise repeat the algorithm increasing the proof by one.
     * */
    
    // implement as tail recursive
    def proofOfWork(lastHash: String): Long = {
        @tailrec
        def powHelper(lastHash: String, proof: Long): Long = {
            if (validProof(lastHash, proof))
                proof
            else
                powHelper(lastHash, proof + 1)
        }
    
        val proof = 0
        powHelper(lastHash, proof)
    }
    
    def validProof(lastHash: String, proof: Long): Boolean = {
        // check if the proof we are testing is correct
        val guess = (lastHash ++ proof.toString).toJson.toString
        val guessHash = Crypto.sha256Hash(guess)
        (guessHash take 4) == "0000"
    }
}
