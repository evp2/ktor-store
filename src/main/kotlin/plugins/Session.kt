package com.github.evp2.plugins

import com.github.evp2.model.Session
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie

fun Application.configureSessions() {
    install(Sessions) {
        cookie<Session>("session_id") {
            cookie.path = "/"
            cookie.maxAgeInSeconds = 60
        }
    }
}
