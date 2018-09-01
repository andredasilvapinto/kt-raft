package me.andresp.cluster

import org.slf4j.Logger
import org.slf4j.LoggerFactory


// TODO Strange that cluster is inside Cluster ?
class Node(val nodeAddress: NodeAddress, val cluster: Cluster) {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(Node::class.java)
    }

    // TODO: needs to be persisted to disk
    // TODO: Improve thread-safety of conditional actions
    @Volatile
    var currentElectionTerm = Term(0, null)
        private set

    fun newTerm() {
        setCurrentElectionTerm(currentElectionTerm.number + 1)
    }

    @Synchronized
    fun handleNewLeaderTerm(newLeader: NodeAddress, newLeaderElectionTerm: Int) {
        if (newLeaderElectionTerm < currentElectionTerm.number) {
            throw IllegalArgumentException("Trying to set older leader $newLeader from term $newLeaderElectionTerm when we are already in term $currentElectionTerm")
        } else {
            if (newLeader != cluster.leader) {
                cluster.updateLeader(newLeader)
                logger.info("Changed leader to $newLeader")
            }
            if (newLeaderElectionTerm > currentElectionTerm.number) {
                setCurrentElectionTerm(newLeaderElectionTerm)
            }
        }
    }

    fun setCurrentElectionTerm(electionTerm: Int) {
        currentElectionTerm = Term(electionTerm, null)
        logger.info("Changed current election term to $currentElectionTerm")
    }
}

data class NodeAddress(val host: String, val port: Int)


data class Term(val number: Int, @Volatile var votedFor: NodeAddress?)
