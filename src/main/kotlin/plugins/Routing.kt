package com.github.evp2.plugins

import com.github.evp2.model.Product
import com.github.evp2.model.Session
import com.github.evp2.persistence.ProductDatabase
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.plugins.origin
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.css.*
import kotlinx.html.*

suspend inline fun ApplicationCall.respondCss(builder: CssBuilder.() -> Unit) {
    this.respondText(CssBuilder().apply(builder).toString(), ContentType.Text.CSS)
}

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
        get("/styles.css") {
            call.respondCss {
                body {
                    fontFamily = "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Fira Sans', 'Droid Sans', 'Helvetica Neue', Arial, sans-serif"
                    backgroundColor = Color("#f6f7fb")
                    color = Color("#1f2330")
                }
                rule(".container") {
                    minHeight = 100.vh
                    display = Display.flex
                    alignItems = Align.center
                    justifyContent = JustifyContent.center
                    padding = Padding(24.px)
                }
                rule(".form-card") {
                    width = LinearDimension("100%")
                    maxWidth = 420.px
                    backgroundColor = Color.white
                    borderRadius = 12.px
                    padding = Padding(28.px)
                    border = Border(1.px, BorderStyle.solid, Color("#e6e8ef"))
                }
                rule(".form-card h1") {
                    fontSize = 24.px
                    marginBottom = 16.px
                    fontWeight = FontWeight.bold
                }
                rule("form") {
                    display = Display.block
                    width = 100.pct
                    boxSizing = BoxSizing.borderBox
                }
                rule("label") {
                    display = Display.block
                    fontSize = 14.px
                    color = Color("#333")
                    marginBottom = 6.px
                }
                rule("input[type=text], input[type=password]") {
                    width = 100.pct
                    padding = Padding(12.px)
                    borderRadius = 8.px
                    border = Border(1.px, BorderStyle.solid, Color("#cfd5e4"))
                    backgroundColor = Color.white
                    marginBottom = 14.px
                    fontSize = 14.px
                    boxSizing = BoxSizing.borderBox
                }
                rule("input[type=text]:focus, input[type=password]:focus") {
                    outline = Outline.none
                    border = Border(1.px, BorderStyle.solid, Color("#6b8cff"))
                }
                rule(".actions") {
                    display = Display.flex
                    alignItems = Align.center
                    justifyContent = JustifyContent.spaceBetween
                    marginTop = 6.px
                }
                rule("input[type=submit]") {
                    backgroundColor = Color("#0f172a")
                    color = Color.white
                    padding = Padding(12.px, 18.px)
                    borderRadius = 8.px
                    border = Border.none
                    fontSize = 14.px
                    fontWeight = FontWeight.bold
                    cursor = Cursor.pointer
                }
                rule("input[type=submit]:hover") {
                    backgroundColor = Color("#1557c0")
                }
                rule(".back-link a") {
                    color = Color("#1f6feb")
                }
                rule(".error") {
                    backgroundColor = Color("#fde8e8")
                    color = Color("#b91c1c")
                    border = Border(1.px, BorderStyle.solid, Color("#f5c2c2"))
                    padding = Padding(10.px, 12.px)
                    borderRadius = 8.px
                    marginBottom = 14.px
                    fontSize = 14.px
                }
            }
        }
        get("/login") {
            val showError = call.request.queryParameters["error"] != null
            call.respondHtml {
                head {
                    link(rel = "stylesheet", href = "/styles.css", type = "text/css")
                }
                body {
                    div(classes = "container") {
                        div(classes = "form-card") {
                            h1 { +"Sign in" }
                            if (showError) {
                                div(classes = "error") { +"Invalid username or password. Please try again." }
                            }
                            form(action = "/login", encType = FormEncType.applicationXWwwFormUrlEncoded, method = FormMethod.post) {
                                label {
                                    htmlFor = "username"
                                    +"Username:"
                                }
                                textInput(name = "username") { id = "username"; placeholder = "Enter your username" }

                                label {
                                    htmlFor = "password"
                                    +"Password:"
                                }
                                passwordInput(name = "password") { id = "password"; placeholder = "Enter your password" }

                                div(classes = "actions") {
                                    submitInput { value = "Login" }
                                    div(classes = "back-link") {
                                        a(href = "/store") { +"Back to store" }
                                    }
                                }
                            }
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
                val remoteIp = call.request.origin.remoteHost
                val userAgent = call.request.userAgent().orEmpty()
                // Log successful attempt and reset limiter state for IP
                call.application.log.info("Login SUCCESS ip={} username={} ua={}", remoteIp, userName, userAgent)
                LoginRateLimiter.recordSuccess(remoteIp)
                call.sessions.set(Session(name = userName, count = 1, remoteIp, userAgent))
                call.respondRedirect("/welcome")
            }
        }
        authenticate("authenticated") {
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
