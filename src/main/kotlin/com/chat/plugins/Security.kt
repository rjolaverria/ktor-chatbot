package com.chat.plugins

import com.chat.clients.SessionsTable
import io.ktor.server.application.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import kotlinx.serialization.Serializable

@Serializable
data class ChatSession(val id: String)

class ChatSessionStorage : SessionStorage {
    override suspend fun read(id: String): String {
        return SessionsTable.get(id) ?: throw NoSuchElementException("Session $id not found")
    }

    override suspend fun write(id: String, value:String) {
        SessionsTable.put(id, value)
    }

    override suspend fun invalidate(id: String) {
        SessionsTable.delete(id)
    }
}

fun Application.configureSecurity() {
    install(Sessions) {
        cookie<ChatSession>("SESSION", ChatSessionStorage()) {
            cookie.extensions["SameSite"] = "lax"
        }
    }

    intercept(ApplicationCallPipeline.Plugins) {
        if (call.sessions.get<ChatSession>() == null) {
            call.sessions.set(ChatSession(generateNonce()))
        }
    }
}
