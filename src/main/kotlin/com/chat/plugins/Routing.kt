package com.chat.plugins

import com.chat.Bot
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello World!")
        }
    }
    routing {
        webSocket("/bot"){
            Bot.handleWebsocket(this)
        }
    }
}
