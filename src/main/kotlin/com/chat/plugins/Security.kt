package com.chat.plugins

import com.azure.ai.openai.models.ChatRequestMessage
import io.ktor.server.application.*
import io.ktor.server.sessions.*
import java.util.*

class ChatSessionEntity {
    val sessionId: UUID = UUID.randomUUID()
    var lastActivityAt: Long = System.currentTimeMillis()
    val history: MutableList<ChatRequestMessage> = mutableListOf()

    fun onUserActivity() {
        this.lastActivityAt = System.currentTimeMillis()
    }
}

fun Application.configureSecurity() {
    install(Sessions) {
        cookie<ChatSessionEntity>("CHAT_SESSION")
    }

    intercept(ApplicationCallPipeline.Plugins) {
        if (call.sessions.get<ChatSessionEntity>() == null) {
            var sessionId = call.parameters["sessionId"].orEmpty()
            if (sessionId.isNullOrEmpty()) {
                call.sessions.set(ChatSessionEntity())
            }
        }
    }
}
