
val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val koin_version: String by project
val azure_ai_openai_version: String by project
val pinecone_client_version: String by project
val aws_s3_version: String by project
val aws_dynamodb_version: String by project
val dot_env_version: String by project

val postgres_version: String by project
val h2_version: String by project
plugins {
    kotlin("jvm") version "1.9.22"
    id("io.ktor.plugin") version "2.3.8"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
}

group = "com.example"
version = "0.0.1"

application {
    mainClass.set("com.example.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }

}

dependencies {
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-websockets-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("org.postgresql:postgresql:$postgres_version")
    implementation("com.h2database:h2:$h2_version")
    implementation("io.ktor:ktor-server-sessions-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("com.github.dotenv-org:dotenv-vault-kotlin:$dot_env_version")
    testImplementation("io.ktor:ktor-server-tests-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")


    // Koin
    implementation("io.insert-koin:koin-core:$koin_version")
    implementation("io.insert-koin:koin-ktor:$koin_version")
    implementation("io.insert-koin:koin-logger-slf4j:$koin_version")

    // Azure OpenAI
    implementation("com.azure:azure-ai-openai:$azure_ai_openai_version")

    // Pinecone
    implementation("io.pinecone:pinecone-client:$pinecone_client_version")

    // AWS
    implementation("aws.sdk.kotlin:s3:$aws_s3_version")
    implementation("aws.sdk.kotlin:dynamodb:$aws_dynamodb_version")
}
