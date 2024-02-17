package com.example.plugins

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.smithy.kotlin.runtime.content.decodeToString
import aws.smithy.kotlin.runtime.io.use
import com.azure.ai.openai.OpenAIClientBuilder
import com.azure.ai.openai.models.*
import com.azure.core.credential.KeyCredential
import com.google.protobuf.Value
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import io.pinecone.PineconeClient
import io.pinecone.PineconeClientConfig
import io.pinecone.proto.QueryRequest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.dotenv.vault.dotenvVault
import java.time.Duration
import java.util.*
import kotlin.time.Duration.Companion.minutes

val SESSION_TIMEOUT = 10.minutes.inWholeMilliseconds


object UUIDSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }
}

enum class Status{
    TERMINATED,
    EMBEDDING,
    SEARCHING,
    PROCESSING,
    ANSWER
}

@Serializable
data class ServerMessage(
    @Serializable(with = UUIDSerializer::class)
    val sessionId: UUID? = null,
    val status: Status? =  null,
    val message: String? = null,
    val sources: List<String?>? = null
)

@Serializable
data class UserMessage(
    @Serializable(with = UUIDSerializer::class)
    val sessionId: UUID,
    val message: String
)

fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
    }
    routing {
        webSocket("/bot") { // websocketSession
            val session = call.sessions.get<ChatSessionEntity>()
            val dotenv = dotenvVault()


            sendSerialized(ServerMessage(sessionId = session?.sessionId))

            incoming.consumeAsFlow()
                .mapNotNull { it as? Frame.Text }
                .map { it.readText() }
                .map { Json.decodeFromString<UserMessage>(it) }
                .onCompletion {
                    it?.printStack()
                }
                .collect {
                    if (session == null) {
                        val msg = "No session found."
                        sendSerialized(ServerMessage(status = Status.TERMINATED, message = msg))
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, msg ))
                        return@collect
                    }

                    if (it.sessionId != session.sessionId) {
                        val msg = "Invalid session"
                        sendSerialized(ServerMessage(status = Status.TERMINATED, message = msg))
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, msg))
                        return@collect
                    }

                    if (System.currentTimeMillis() - session.lastActivityAt > SESSION_TIMEOUT) {
                        val msg = "Inactive for too long"
                        sendSerialized(ServerMessage(status = Status.TERMINATED, message = msg))
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, msg))
                        return@collect
                    }
                    if (it.message.equals("bye", ignoreCase = true)) {
                        val msg = " said BYE"
                        sendSerialized(ServerMessage(status = Status.TERMINATED, message = "You$msg"))
                        close(CloseReason(CloseReason.Codes.NORMAL, "Client$msg"))
                        return@collect
                    }

                    session.onUserActivity()
                    session.history.add(
                        ChatRequestSystemMessage("""
                            You are a helpful assistant that answers questions. 
                            Only use the information provided under "Context" or the previous messages if and only if it pertains to the context. 
                            If the question can't be answered based on the context or previous messages, say "I don't know"
                        """.trimIndent())
                    )

                    // Embed question
                    sendSerialized(ServerMessage(status = Status.EMBEDDING))
                    val openAI = OpenAIClientBuilder()
                        .credential(KeyCredential(dotenv["OPENAI_API_KEY"]))
                        .buildClient()
                    val embeddingsOptions = EmbeddingsOptions(listOf(it.message));
                    val embeddings = openAI.getEmbeddings(dotenv["EMBEDDING_MODEL"], embeddingsOptions)
                    val embed = embeddings.data[0].embedding

                    // Query by vector to verify
                    sendSerialized(ServerMessage(status = Status.SEARCHING))
                    val pineconeConfig =
                        PineconeClientConfig().withApiKey(dotenv["PINECONE_API_KEY"]).withEnvironment(dotenv["PINECONE_ENV"])
                            .withProjectName(dotenv["PINECONE_PROJECT_NAME"])
                    val pineconeClient = PineconeClient(pineconeConfig)
                    val pinecone = pineconeClient.connect(dotenv["PINECONE_INDEX_NAME"])
                    val queryRequest = QueryRequest.newBuilder()
                        .setTopK(2)
                        .setIncludeValues(true)
                        .setIncludeMetadata(true)
                        .addAllVector(embed.map { v -> v.toFloat() })
                        .build()
                    val response = pinecone.blockingStub.query(queryRequest)
                    val matches = response.matchesList
                        .filter { match -> match.score > 0.75 }
                        .map { match -> match.metadata.fieldsMap }
                    val keys = matches
                        .map { match -> match.getOrDefault("s3_key", Value.getDefaultInstance()).stringValue }
                    val sources = matches
                        .map { match -> match.getOrDefault("source_url", Value.getDefaultInstance()).stringValue }
                        .distinct()

                    // Fetch docs
                    val texts = S3Client { region = "us-east-1" }.use { client ->
                        keys.mapNotNull { s3Key ->
                            val req = GetObjectRequest {
                                key = s3Key
                                bucket = dotenv["S3_BUCKET_NAME"]
                            }
                            client.getObject(req) { resp ->
                                resp.body?.decodeToString()
                            }
                        }
                    }
                    sendSerialized(ServerMessage(status = Status.PROCESSING))

                    // Add user message to history
                    session.history.add(
                        ChatRequestUserMessage("Context: ${texts.joinToString("\n")}\n\\n---\\n\\nQuestion: ${it.message}\\nAnswer:")
                    )

                    // Ask GPT to respond
                    val completion = openAI
                        .getChatCompletions(dotenv["COMPLETION_MODEL"], ChatCompletionsOptions(session.history))

                    val choices = completion.choices
                        .filter { choice -> choice.message.role != ChatRole.USER }

                    // Add system message to history
                    session.history.addAll(choices.mapNotNull { choice -> ChatRequestAssistantMessage(choice.message.content) })

                    val message = choices.map { choice -> choice.message.content }.firstOrNull()

                    sendSerialized(ServerMessage(status = Status.ANSWER, message = message, sources = sources))
                }
        }
    }
}

