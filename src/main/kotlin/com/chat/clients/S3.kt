package com.chat.clients

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.smithy.kotlin.runtime.content.decodeToString
import com.chat.config.Env
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object S3 {
    suspend fun getObject(s3key: String): String? = withContext(Dispatchers.IO) {
        GetObjectRequest {
            key = s3key
            bucket = Env.s3BucketName
        }.let {
            S3Client { region = "us-east-1" }
                .getObject(it) { response ->
                    response.body?.decodeToString()
                }
        }
    }
}
