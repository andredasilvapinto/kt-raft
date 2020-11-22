package me.andresp.cluster

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.*
import java.util.concurrent.atomic.AtomicReference


// TODO Strange that cluster is inside Node ?
class Node(val nodeAddress: NodeAddress, val cluster: Cluster) {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(Node::class.java)
    }

    private val termFilePath = "term_${nodeAddress.port}.sav"

    private var currentElectionTermRef = AtomicReference<Term>(
            if (File(termFilePath).exists()) {
                Term.load(termFilePath)
            } else {
                Term(0, null, null)
            }
    )

    var currentElectionTerm = currentElectionTermRef.get()!!
        get() = currentElectionTermRef.get()

    @Synchronized
    fun newTerm() {
        setCurrentElectionTerm(currentElectionTermRef.get().number + 1, null)
    }

    // TODO: Still need synchronized because of atomic change of leader and term (perhaps move leader inside Term?)
    @Synchronized
    fun handleNewLeaderTerm(newLeader: NodeAddress, newLeaderElectionTerm: Int) {
        if (newLeaderElectionTerm < currentElectionTerm.number) {
            throw IllegalArgumentException("Trying to set older leader $newLeader from term $newLeaderElectionTerm when we are already in term $currentElectionTermRef")
        } else if (newLeader != currentElectionTerm.leaderAddress || newLeaderElectionTerm > currentElectionTerm.number) {
            setCurrentElectionTerm(newLeaderElectionTerm, newLeader)
            logger.info("Changed term to $currentElectionTerm")
        }
    }

    @Synchronized
    fun setCurrentElectionTerm(electionTerm: Int, leaderAddress: NodeAddress?) {
        currentElectionTermRef.set(Term(electionTerm, leaderAddress, null))
        currentElectionTerm.save(termFilePath)
        logger.info("Changed current election term to $currentElectionTerm")
    }

    fun updateTermIfNewer(electionTerm: Int): Boolean {
        val (oldTerm, _) = currentElectionTermRef.getAndUpdate {
            if (electionTerm > it.number) {
                // TODO: Who is the leader now?
                logger.info("Changed current election term to $electionTerm")
                currentElectionTerm.save(termFilePath)
                Term(electionTerm, null, null)
            } else {
                it
            }
        }
        return electionTerm > oldTerm
    }
}

data class NodeAddress(val host: String, val port: Int) : Serializable


data class Term(val number: Int, @Volatile var leaderAddress: NodeAddress?, @Volatile var votedFor: NodeAddress?) : Serializable {
    fun save(filePath: String) {
        ObjectOutputStream(FileOutputStream(filePath)).use { it.writeObject(this) }
    }

    companion object {
        fun load(filePath: String): Term =
                ObjectInputStream(FileInputStream(filePath)).use {
                    val obj = it.readObject()
                    when (obj) {
                        is Term -> obj
                        else -> throw RuntimeException("Error desserializing $filePath. Expected Term, found $obj")
                    }
                }
    }
}
