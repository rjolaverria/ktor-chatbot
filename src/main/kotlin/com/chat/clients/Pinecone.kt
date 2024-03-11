package com.chat.clients

import com.chat.config.Env
import io.pinecone.PineconeClient
import io.pinecone.PineconeClientConfig
import io.pinecone.proto.QueryRequest
import io.pinecone.proto.QueryResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Pinecone {
    private val client = PineconeClient(
        PineconeClientConfig()
            .withApiKey(Env.pineconeApiKey)
            .withEnvironment(Env.pineconeEnv)
            .withProjectName(Env.pineconeProject)
    ).connect(Env.pineconeIndex)


    suspend fun query(embed: List<Float>, topK: Int = 2): QueryResponse = withContext(Dispatchers.IO) {
        client.blockingStub.query(QueryRequest.newBuilder()
            .setTopK(topK)
            .setIncludeValues(true)
            .setIncludeMetadata(true)
            .addAllVector(embed)
            .build()
        )
    }
}