package com.chat.clients

import aws.sdk.kotlin.services.dynamodb.DynamoDbClient
import aws.sdk.kotlin.services.dynamodb.model.*
import com.chat.config.Env

object SessionsTable {
    private const val partitionKeyName = "sessionId"

    suspend fun get(sessionId: String): String? {
        val keyToGet = mutableMapOf<String, AttributeValue>()
        keyToGet[partitionKeyName] = AttributeValue.S(sessionId)

        val request = GetItemRequest {
            key = keyToGet
            tableName = Env.sessionsTableName
        }

        DynamoDbClient { region = "us-east-1" }.use { ddb ->
            val returnedItem = ddb.getItem(request)
            val item = returnedItem.item
            return item?.get("value")?.asSOrNull()
        }
    }

    suspend fun put(
        sessionId: String,
        value: String
    ) {
        val itemValues = mutableMapOf<String, AttributeValue>()

        itemValues[partitionKeyName] = AttributeValue.S(sessionId)
        itemValues["value"] = AttributeValue.S(value)
        itemValues["createdAt"] = AttributeValue.S(System.currentTimeMillis().toString())

        val request = PutItemRequest {
            tableName = Env.sessionsTableName
            item = itemValues
        }

        DynamoDbClient { region = "us-east-1" }.use { ddb ->
            ddb.putItem(request)
            println(" A new item was placed into ${Env.sessionsTableName}.")
        }
    }

    suspend fun delete(sessionId: String) {
        val keyToGet = mutableMapOf<String, AttributeValue>()
        keyToGet[partitionKeyName] = AttributeValue.S(sessionId)

        val request = DeleteItemRequest {
            tableName = Env.sessionsTableName
            key = keyToGet
        }

        DynamoDbClient { region = "us-east-1" }.use { ddb ->
            ddb.deleteItem(request)
            println("sessionId: $sessionId was deleted")
        }
    }

}