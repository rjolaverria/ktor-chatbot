package com.chat

import com.azure.ai.openai.models.ChatRequestAssistantMessage
import com.azure.ai.openai.models.ChatRequestSystemMessage
import com.azure.ai.openai.models.ChatRequestUserMessage
import com.azure.ai.openai.models.ChatRole
import com.chat.clients.OpenAI
import com.chat.clients.Pinecone
import com.chat.clients.S3
import com.chat.plugins.ChatSessionEntity
import com.google.protobuf.Value
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.serialization.json.Json

import kotlin.time.Duration.Companion.minutes

val SESSION_TIMEOUT = 10.minutes.inWholeMilliseconds

object Bot {
    suspend fun handleWebsocket(session: DefaultWebSocketServerSession) {
        session.apply {
            val session = call.sessions.get<ChatSessionEntity>()

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
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, msg))
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
                    val embed = OpenAI.getEmbedding(it.message)

                    // Query by vector to verify
                    sendSerialized(ServerMessage(status = Status.SEARCHING))

                    val response = Pinecone.query(embed)
                    val matches = response.matchesList
                        .filter { match -> match.score > 0.75 }
                        .map { match -> match.metadata.fieldsMap }
                    val keys = matches
                        .map { match -> match.getOrDefault("s3_key", Value.getDefaultInstance()).stringValue }
                    val sources = matches
                        .map { match -> match.getOrDefault("source_url", Value.getDefaultInstance()).stringValue }
                        .distinct()

                    // Fetch docs
                    val texts = keys.mapNotNull { key -> S3.getObject(key) }

                    sendSerialized(ServerMessage(status = Status.PROCESSING))
                    // Add user message to history
                    session.history.add(
                        ChatRequestUserMessage("Context: ${texts.joinToString("\n")}\n\\n---\\n\\nQuestion: ${it.message}\\nAnswer:")
                    )

                    // Ask GPT to respond
                    val completions = OpenAI.getCompletions(session.history)

                    val choices = completions.choices
                        .filter { choice -> choice.message.role != ChatRole.USER }

                    // Add system message to history
                    session.history.addAll(choices.mapNotNull { choice -> ChatRequestAssistantMessage(choice.message.content) })

                    val message = choices.map { choice -> choice.message.content }.firstOrNull()

                    sendSerialized(ServerMessage(status = Status.ANSWER, message = message, sources = sources))
                }
        }
    }
}