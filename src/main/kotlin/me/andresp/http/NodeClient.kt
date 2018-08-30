package me.andresp.http

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import me.andresp.api.AskVotePayload
import me.andresp.cluster.ClusterStatus
import me.andresp.cluster.NodeAddress
import me.andresp.statemachine.AskVoteReply
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class NodeClient(private val httpClient: HttpClient) {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(NodeClient::class.java)
    }

    suspend fun join(targetAddress: NodeAddress, joinerAddress: NodeAddress): ClusterStatus {
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
        return clusterStatus
    }

    suspend fun askForVote(targetAddress: NodeAddress, candidateAddress: NodeAddress, electionTerm: Int): AskVoteReply {
        val askVoteReply = httpClient.request<AskVoteReply> {
            url {
                host = targetAddress.host
                port = targetAddress.port
                encodedPath = "/cluster/ask-vote/$electionTerm"
            }
            method = HttpMethod.Put
            // TODO Implement lastlog args
            body = AskVotePayload(electionTerm, candidateAddress, 1L, 1)
            contentType(ContentType.Application.Json)
        }

        logger.info("Received response to candidate request from $targetAddress: $askVoteReply")
        return askVoteReply
    }

//    suspend fun voteFor(voterAddress: NodeAddress, candidateAddress: NodeAddress, electionTerm: Int) {
//        httpClient.post<NodeAddress>(
//                host = candidateAddress.host,
//                port = candidateAddress.port,
//                path = "/cluster/vote/$electionTerm",
//                body = voterAddress
//        ) {
//            contentType(ContentType.Application.Json)
//        }
//    }

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

