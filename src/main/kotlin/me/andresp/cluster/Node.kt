package me.andresp.cluster


// TODO Strange that cluster is inside Cluster ?
class Node(val nodeAddress: NodeAddress, val cluster: Cluster) {
    // TODO: needs to be persisted to disk
    // TODO: Improve thread-safety of conditional actions
    @Volatile
    var currentElectionTerm = Term(1, null)
        private set

    fun newTerm() {
        currentElectionTerm = Term(currentElectionTerm.number + 1, null)
    }

    @Synchronized
    fun handleNewLeader(newLeader: NodeAddress, newLeaderElectionTerm: Int) {
        if (newLeaderElectionTerm < currentElectionTerm.number) {
            throw IllegalArgumentException("Trying to set older leader $newLeader from term $newLeaderElectionTerm when we are already in term $currentElectionTerm")
        } else {
            cluster.updateLeader(newLeader)
            if (newLeaderElectionTerm > currentElectionTerm.number) {
                setCurrentElectionTerm(newLeaderElectionTerm)
            }
        }
    }

    fun setCurrentElectionTerm(electionTerm: Int) {
        currentElectionTerm = Term(electionTerm, null)
    }
}

data class NodeAddress(val host: String, val port: Int)


data class Term(val number: Int, @Volatile var votedFor: NodeAddress?)
