package com.chat.clients

import com.chat.config.Env
import com.google.api.Logging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.*


@Serializable
data class RerankRequest(
    val model: String,
    val query: String,
    val top_n: Int,
    val documents: List<String>
)

@Serializable
data class RerankResponse(
    val id: String,
    val results: List<Result>,
    val meta: Meta
)

@Serializable
data class Result(
    val index: Int,
    val relevance_score: Double
)

@Serializable
data class Meta(
    val api_version: ApiVersion,
    val billed_units: BilledUnits
)

@Serializable
data class ApiVersion(
    val version: String
)

@Serializable
data class BilledUnits(
    val search_units: Int
)

object Cohere {
    private const val baseUrl = "https://api.cohere.ai/v1"

    suspend fun rerank(query: String, texts: List<String>): RerankResponse {
        val request = RerankRequest(
            model = "rerank-english-v2.0",
            query = query,
            top_n = 3,
            documents = texts
        )
        return HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json()
            }
        }.use { client ->
            client.post("$baseUrl/rerank") {
                header("Authorization", "Bearer ${Env.cohereApiKey}")
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        }
    }
}