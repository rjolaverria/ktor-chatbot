package com.chat.clients

import com.azure.ai.openai.OpenAIClientBuilder
import com.azure.ai.openai.models.ChatCompletions
import com.azure.ai.openai.models.ChatCompletionsOptions
import com.azure.ai.openai.models.ChatRequestMessage
import com.azure.ai.openai.models.EmbeddingsOptions
import com.azure.core.credential.KeyCredential
import com.chat.config.Env
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object OpenAI {
    private val client = OpenAIClientBuilder()
        .credential(KeyCredential(Env.openAIKey))
        .buildClient()

    suspend fun getEmbedding(text: String): List<Float> = withContext(Dispatchers.IO) {
        client.getEmbeddings(Env.embeddingModel, EmbeddingsOptions(listOf(text)))
            .data[0].embedding.map { it.toFloat() }
    }

    suspend fun getCompletions(chatRequestMessages: List<ChatRequestMessage>): ChatCompletions =
        withContext(Dispatchers.IO) {
            client.getChatCompletions(Env.completionModel, ChatCompletionsOptions(chatRequestMessages))
        }
}