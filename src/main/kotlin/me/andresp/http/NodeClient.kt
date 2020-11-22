package me.andresp.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.request
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.*
import me.andresp.api.AppendEntriesReply
import me.andresp.api.AskVotePayload
import me.andresp.api.NodeJoinedPayload
import me.andresp.cluster.Cluster
import me.andresp.cluster.ClusterStatus
import me.andresp.cluster.NodeAddress
import me.andresp.statemachine.AppendEntries
import me.andresp.statemachine.AskVoteReply
import me.andresp.statemachine.LeaderHeartbeat
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class NodeClient(private val selfAddress: NodeAddress, private val httpClient: HttpClient) {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(NodeClient::class.java)

        fun defaultClient(selfAddress: NodeAddress): NodeClient =
                NodeClient(selfAddress, HttpClient(Apache.create {
                    socketTimeout = 5_000
                    connectTimeout = 5_000
                    connectionRequestTimeout = 10_000

                    customizeClient {
                        setMaxConnTotal(100)
                        setMaxConnPerRoute(100)
                    }
                }) { install(JsonFeature) })

        fun broadcast(nodeAddresses: Set<NodeAddress>, f: suspend (NodeAddress) -> Unit) = nodeAddresses.map { GlobalScope.launch { f(it) } }

        fun broadcastAndWait(nodeAddresses: Set<NodeAddress>, f: suspend (NodeAddress) -> Unit) = runBlocking { nodeAddresses.map { async { f(it) } }.awaitAll() }
    }

    suspend fun sendJoinNotification(targetAddress: NodeAddress, nodeJoinedPayload: NodeJoinedPayload): ClusterStatus =
            putJson(targetAddress, ENDPOINT_CLUSTER_JOIN, nodeJoinedPayload)

    suspend fun sendRequestForVote(targetAddress: NodeAddress, askVotePayload: AskVotePayload): AskVoteReply =
            putJson(targetAddress, "$ENDPOINT_CLUSTER_ASK_VOTE/${askVotePayload.electionTerm}", askVotePayload)

    suspend fun sendHeartbeat(targetAddress: NodeAddress, leaderHearbeat: LeaderHeartbeat): String =
            putJson(targetAddress, ENDPOINT_CLUSTER_HEARTBEAT, leaderHearbeat)

    suspend fun sendAppendEntry(targetAddress: NodeAddress, appendEntries: AppendEntries): AppendEntriesReply =
            putJson(targetAddress, ENDPOINT_LOG_APPEND, appendEntries)

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

    fun broadcast(cluster: Cluster, f: suspend (NodeAddress) -> Unit) = broadcast(cluster.nodeAddresses.minus(selfAddress), f)

    fun broadcastAndWait(cluster: Cluster, f: suspend (NodeAddress) -> Unit) = broadcastAndWait(cluster.nodeAddresses.minus(selfAddress), f)
}

