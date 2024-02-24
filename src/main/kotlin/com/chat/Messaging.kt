package com.chat

import com.azure.ai.openai.models.ChatRequestAssistantMessage
import com.azure.ai.openai.models.ChatRequestMessage
import com.azure.ai.openai.models.ChatRequestSystemMessage
import com.azure.ai.openai.models.ChatRequestUserMessage
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Transient
import kotlinx.serialization.Serializable
import kotlinx.datetime.serializers.InstantIso8601Serializer
import java.util.*

enum class MessageType {
    ASSISTANT,
    SYSTEM,
    STATUS,
    USER,
}

enum class Status{
    TERMINATED,
    EMBEDDING,
    SEARCHING,
    PROCESSING,
}

@Serializable
data class Message(
    val type: MessageType,
    @Serializable(with = InstantIso8601Serializer::class)
    val sentAt: Instant = Clock.System.now(),
    val text: String,
    @Transient
    val context: String? = null,
    val sources: List<String> = emptyList(),
    val sessionId: String? = null,
) {
    fun toChatRequestMessage(): ChatRequestMessage? {
        return when (type) {
            MessageType.ASSISTANT -> ChatRequestAssistantMessage(text)
            MessageType.SYSTEM -> ChatRequestSystemMessage(text)
            MessageType.USER -> ChatRequestUserMessage("Context: ${context}\n\\n---\\n\\nQuestion: ${text}\\nAnswer:")
            MessageType.STATUS -> null
        }
    }
}

@Serializable
data class Conversation(
    val sessionId: String,
    @Serializable(with = InstantIso8601Serializer::class)
    val createdAt: Instant = Clock.System.now(),
    val id: String = UUID.randomUUID().toString(),
    val messages: MutableList<Message> = mutableListOf()
) {
    @Serializable(with = InstantIso8601Serializer::class)
    var lastMessageAt: Instant = Clock.System.now()
        private set

    fun isNew(): Boolean {
        return messages.isEmpty()
    }

    fun addMessage(newMessage: Message) {
        messages.add(newMessage)
        lastMessageAt = newMessage.sentAt
    }

    fun toChatRequestMessages(): List<ChatRequestMessage> {
        return messages.mapNotNull { it.toChatRequestMessage() }
    }

    fun addAllMessages(newMessages: List<Message>) {
        messages.addAll(newMessages)
        lastMessageAt = newMessages.last().sentAt
    }

}
