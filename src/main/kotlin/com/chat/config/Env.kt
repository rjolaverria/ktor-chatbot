package com.chat.config

import org.dotenv.vault.dotenvVault

object Env {
    private val dotenv = dotenvVault()

    val openAIKey: String? = dotenv["OPENAI_API_KEY"] ?: ""

    val embeddingModel: String? = dotenv["EMBEDDING_MODEL"] ?: "text-embedding-ada-002"

    val completionModel: String? = dotenv["COMPLETION_MODEL"] ?: "gpt-3.5-turbo"

    val pineconeApiKey: String? = dotenv["PINECONE_API_KEY"] ?: ""

    val pineconeEnv: String? = dotenv["PINECONE_ENV"] ?: ""

    val pineconeProject: String? = dotenv["PINECONE_PROJECT_NAME"] ?: ""

    val pineconeIndex: String? = dotenv["PINECONE_INDEX_NAME"] ?: "embeddings"

    val s3BucketName: String? = dotenv["S3_BUCKET_NAME"] ?: ""

    val sessionsTableName: String? = dotenv["SESSIONS_TABLE_NAME"] ?: ""
}