package me.andresp.http

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import me.andresp.cluster.ClusterStatus
import me.andresp.cluster.NodeAddress
import org.slf4j.LoggerFactory


val logger = LoggerFactory.getLogger("NodeClient")!!

class NodeClient(private val httpClient: HttpClient) {

    suspend fun join(targetAddress: NodeAddress, joinerAddress: NodeAddress) {
        val clusterStatus = httpClient.request<ClusterStatus> {
            url {
                host = targetAddress.host
                port = targetAddress.port
                encodedPath = "/cluster/join"
            }
            method = HttpMethod.Put
            body = joinerAddress
            contentType(ContentType.Application.Json)
        }

        logger.info("Received cluster status: $clusterStatus")
    }

    suspend fun voteFor(voterAddress: NodeAddress, candidateAddress: NodeAddress, electionTerm: Int) {
        httpClient.post<NodeAddress>(
                host = candidateAddress.host,
                port = candidateAddress.port,
                path = "/cluster/vote/$electionTerm",
                body = voterAddress
        ) {
            contentType(ContentType.Application.Json)
        }
    }

    suspend fun sendClusterStatus(destNode: NodeAddress, clusterStatus: ClusterStatus) {
        httpClient.post<ClusterStatus>(
                host = destNode.host,
                port = destNode.port,
                path = "/cluster/update",
                body = clusterStatus
        ) {
            contentType(ContentType.Application.Json)
        }
    }
}

