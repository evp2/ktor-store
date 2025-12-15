package com.github.evp2.plugins

import com.github.evp2.model.Product
import com.github.evp2.model.Session
import com.github.evp2.persistence.ProductDatabase
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.html.respondHtml
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.request.userAgent
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlinx.html.FormEncType
import kotlinx.html.FormMethod
import kotlinx.html.body
import kotlinx.html.form
import kotlinx.html.p
import kotlinx.html.passwordInput
import kotlinx.html.submitInput
import kotlinx.html.textInput

fun Application.configureRouting() {
    routing {
        staticResources("store", "store")
        get("/") {
            val userSession = call.principal<Session>()
            if (userSession != null) {
                call.sessions.set(userSession.copy(count = userSession.count + 1))
            }
            call.respondRedirect("/store")
        }
        get("/login") {
            call.respondHtml {
                body {
                    form(action = "/login", encType = FormEncType.applicationXWwwFormUrlEncoded, method = FormMethod.post) {
                        p {
                            +"Username:"
                            textInput(name = "username")
                        }
                        p {
                            +"Password:"
                            passwordInput(name = "password")
                        }
                        p {
                            submitInput { value = "Login" }
                        }
                    }
                }
            }
        }
        get("/logout") {
            call.sessions.clear<Session>()
            call.respondRedirect("/login")
        }
        get("/api/products") {
            call.respond(ProductDatabase.dao.products())
        }
        get("/api/products/{id}") {
            val id = call.parameters["id"]
            val upc = id?.toInt()

            if (upc == null) {
                call.respondText("Missing upc", status = HttpStatusCode.BadRequest)
                return@get
            }

            when (val product = ProductDatabase.dao.product(upc)) {
                null -> call.respond(HttpStatusCode.BadRequest)
                else -> call.respond(product)
            }
        }
        authenticate("auth-form") {
            post("/login") {
                val userName = call.principal<UserIdPrincipal>()?.name.toString()
                val remoteIp = call.request.origin.toString()
                val userAgent = call.request.userAgent().toString()
                call.sessions.set(Session(name = userName, count = 1, remoteIp, userAgent))
                call.respondRedirect("/welcome")
            }
        }
        authenticate("auth-session") {
            get("/welcome") {
                val userSession = call.principal<Session>()
                call.sessions.set(userSession?.copy(count = userSession.count + 1))
                call.respondText("Welcome, ${userSession?.name}!")
            }
            post("/products") {
                val body = call.receive<Product>()

                when(val insertedProduct = ProductDatabase.dao.addProduct(body)) {
                    null -> call.respondText("Failed to add product: $body", status = HttpStatusCode.InternalServerError)
                    else -> call.respondText("Added product: $body", status = HttpStatusCode.Created)
                }
            }
        }
    }
}
