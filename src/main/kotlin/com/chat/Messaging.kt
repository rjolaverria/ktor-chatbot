package com.chat

import com.chat.utils.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

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