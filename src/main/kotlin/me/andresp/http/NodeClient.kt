package me.andresp.http

import io.ktor.client.HttpClient
import io.ktor.client.request.request
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.experimental.launch
import me.andresp.api.AskVotePayload
import me.andresp.cluster.Cluster
import me.andresp.cluster.ClusterStatus
import me.andresp.cluster.NodeAddress
import me.andresp.statemachine.AskVoteReply
import me.andresp.statemachine.LeaderHeartbeat
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class NodeClient(private val selfAddress: NodeAddress, private val httpClient: HttpClient) {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(NodeClient::class.java)
    }

    suspend fun join(targetAddress: NodeAddress, joinerAddress: NodeAddress): ClusterStatus =
            putJson(targetAddress, "/cluster/join", joinerAddress)

    suspend fun askForVote(targetAddress: NodeAddress, candidateAddress: NodeAddress, electionTerm: Int): AskVoteReply =
            putJson(targetAddress, "/cluster/ask-vote/$electionTerm", AskVotePayload(electionTerm, candidateAddress, 1L, 1)) // TODO Implement lastlog args

//    suspend fun voteFor(leaderAddress: NodeAddress, candidateAddress: NodeAddress, electionTerm: Int) {
//        httpClient.post<NodeAddress>(
//                host = candidateAddress.host,
//                port = candidateAddress.port,
//                path = "/cluster/vote/$electionTerm",
//                body = leaderAddress
//        ) {
//            contentType(ContentType.Application.Json)
//        }
//    }

    suspend fun sendHeartbeat(targetAddress: NodeAddress, leaderHearbeat: LeaderHeartbeat): String =
            putJson(targetAddress, "/cluster/heartbeat", leaderHearbeat)

    private suspend inline fun <reified T> putJson(targetAddress: NodeAddress, path: String, body: Any): T {
        val response = httpClient.request<T> {
            this.url {
                this.host = targetAddress.host
                this.port = targetAddress.port
                this.encodedPath = path
            }
            this.method = HttpMethod.Put
            this.body = body
            this.contentType(ContentType.Application.Json)
        }

        logger.info("Received from $targetAddress at $path: $response")
        return response
    }

    fun broadcast(cluster: Cluster, f: suspend (NodeAddress) -> Unit) {
        cluster.nodeAddresses.minus(selfAddress).map {
            launch {
                f(it)
            }
        }
    }
}

