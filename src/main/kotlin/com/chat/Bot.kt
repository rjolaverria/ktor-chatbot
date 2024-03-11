package com.chat

import com.azure.ai.openai.models.ChatRole
import com.chat.clients.Cohere
import com.chat.clients.OpenAI
import com.chat.clients.Pinecone
import com.chat.clients.S3
import com.chat.plugins.ChatSession
import com.google.protobuf.Value
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap


object Bot {
    private val sessions = ConcurrentHashMap<String, Conversation>()

    private suspend fun sessionJoined(
        sessionId: String,
        webSocketSession: DefaultWebSocketServerSession,
    ): Conversation {
        return sessions[sessionId]?.also {
            it.messages.filter { message -> message.type != MessageType.SYSTEM }
                .forEach { message ->
                    webSocketSession.sendSerialized(message)
                }
        } ?: Conversation(sessionId).also {
            sessions[sessionId] = it
        }
    }


    suspend fun handleWebsocket(webSocketSession: DefaultWebSocketServerSession) {
        webSocketSession.apply {
            val session = call.sessions.get<ChatSession>()

            if (session == null) {
                val msg = "${Status.TERMINATED}: No session found."
                sendSerialized(Message(type = MessageType.STATUS, text = Status.TERMINATED.toString()))
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, msg))
                return
            }
            val conversation = sessionJoined(session.id, this)

            try {
                incoming.consumeEach { frame ->
                    if(frame is Frame.Text) {
                        val message = Json.decodeFromString<Message>(frame.readText())
                        handleUserMessage(message, session, conversation)
                    }
                }
            } finally {
                return@handleWebsocket
            }
        }
    }

    private suspend fun DefaultWebSocketServerSession.handleUserMessage(
        it: Message,
        session: ChatSession,
        conversation: Conversation,
    ) {
        if (it.sessionId != session.id) {
            val msg = "${Status.TERMINATED}: No session found."
            sendSerialized(Message(type = MessageType.STATUS, text = Status.TERMINATED.toString()))
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, msg))
            return
        }

        if (it.text.equals("bye", ignoreCase = true)) {
            sendSerialized(Message(type = MessageType.STATUS, text = Status.TERMINATED.toString()))
            close(CloseReason(CloseReason.Codes.NORMAL, Status.TERMINATED.toString()))
            return
        }

        // Is the conversation is empty initialize
        if (conversation.isNew()) {
            conversation.addMessage(
                Message(
                    sessionId = session.id,
                    type = MessageType.SYSTEM,
                    text = """
                        You are a helpful assistant that answers questions. 
                        Only use the information provided under "Context" or the previous messages if and only if it pertains to the context. 
                        If the question can't be answered based on the context or previous messages, say "I don't know"
                        """.trimIndent())
            )
        }

        // Embed question
        sendSerialized(Message(type = MessageType.STATUS, text = Status.EMBEDDING.toString()))
        val embed = OpenAI.getEmbedding(it.text)

        // Query by vector to verify
        sendSerialized(Message(type = MessageType.STATUS, text = Status.SEARCHING.toString()))

        val response = Pinecone.query(embed, 5)
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

        // Rerank
        val ranks = if(texts.size > 3) Cohere.rerank(it.text, texts) else null
        val rerankedText = ranks?.results?.map { rank -> texts[rank.index] } ?: texts

        sendSerialized(Message(type = MessageType.STATUS, text = Status.PROCESSING.toString()))
        // Add user message to history
        conversation.addMessage(
            Message(
                sessionId = session.id,
                type = MessageType.USER,
                context = rerankedText.joinToString("\n"),
                text = it.text
            )
        )

        // Ask GPT to respond
        val completions = OpenAI.getCompletions(conversation.toChatRequestMessages())

        val choices = completions.choices
            .filter { choice -> choice.message.role != ChatRole.USER }

        // Add system message to history
        conversation.addAllMessages(choices.mapNotNull { choice ->
            Message(type = MessageType.ASSISTANT,
                text = choice.message.content,
                sources = sources)
        })

        val message = choices.map { choice -> choice.message.content }.firstOrNull() ?: ""

        sendSerialized(Message(type = MessageType.ASSISTANT, text = message, sources = sources))
    }
}