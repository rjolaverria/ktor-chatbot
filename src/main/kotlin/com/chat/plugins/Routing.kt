package com.chat.plugins

import com.chat.Bot
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*


fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("^ _ ^")
        }
    }
    routing {
        webSocket("/bot"){
            Bot.handleWebsocket(this)
        }
    }
    routing {
        get("/api/session") {
            try {
                val session = call.sessions.get<ChatSession>()
                if(session == null){
                    call.response.status(HttpStatusCode.NotFound)
                    call.respondText("No session found.")
                } else {
                    call.respond(session)
                }
            } catch (e: Exception) {
                call.response.status(HttpStatusCode.InternalServerError)
                call.respondText("Internal server error.")
            }

        }
    }
}
