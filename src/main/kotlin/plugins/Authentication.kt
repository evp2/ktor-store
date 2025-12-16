package com.github.evp2.plugins

import com.github.evp2.model.Session
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.*
import io.ktor.server.response.*

fun Application.configureAuthentication() {
    install(Authentication) {
        form("auth-form") {
            userParamName = "username"
            passwordParamName = "password"
            validate { credentials ->
                if (credentials.name == "admin" && credentials.password == "pw") {
                    UserIdPrincipal(credentials.name)
                } else null
            }
            challenge {
                val ip = call.request.origin.remoteHost
                val ua = call.request.userAgent().orEmpty()
                val username = call.parameters["username"].orEmpty()
                if (LoginRateLimiter.isBanned(ip)) {
                    val remaining = LoginRateLimiter.remainingBanSeconds(ip)
                    call.application.log.warn("Login attempt BLOCKED (banned) ip={} username={} ua={} remainingBanSecs={}", ip, username, ua, remaining)
                    call.respond(HttpStatusCode.TooManyRequests, "Too many failed login attempts. Try again in ${remaining}s.")
                } else {
                    // Count this failed attempt
                    LoginRateLimiter.recordFailure(ip)
                    val nowBanned = LoginRateLimiter.isBanned(ip)
                    if (nowBanned) {
                        val remaining = LoginRateLimiter.remainingBanSeconds(ip)
                        call.application.log.warn("Login FAILURE -> BANNED ip={} username={} ua={} remainingBanSecs={}", ip, username, ua, remaining)
                        call.respond(HttpStatusCode.TooManyRequests, "Too many failed login attempts. Try again in ${remaining}s.")
                    } else {
                        call.application.log.warn("Login FAILURE ip={} username={} ua={}", ip, username, ua)
                        call.respondRedirect("/login?error=1")
                    }
                }
            }
        }
        session<Session>("authenticated") {
            validate { session ->
                session
            }
            challenge {
                call.respondRedirect("/login")
            }
        }
    }
}
