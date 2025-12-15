package com.github.evp2

import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationIntTest {

    private fun ApplicationTestBuilder.configureTestApp() {
        application { module() }
    }

    private fun ApplicationTestBuilder.noRedirectClient() = createClient {
        followRedirects = false
    }

    private fun ApplicationTestBuilder.cookieClient(followRedirects: Boolean = true) = createClient {
        this.followRedirects = followRedirects
        install(HttpCookies)
    }

    @Test
    fun `GET root redirects to store and increments session if present`() = testApplication {
        configureTestApp()

        val client = noRedirectClient()
        val response = client.get("/")
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/store", response.headers[HttpHeaders.Location])
    }

    @Test
    fun `GET login returns HTML form`() = testApplication {
        configureTestApp()

        val response = client.get("/login")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "Username:")
        assertContains(body, "Password:")
        assertContains(body, "Login")
    }

    @Test
    fun `static resource store index is served`() = testApplication {
        configureTestApp()

        val response = client.get("/store/index.html")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "Ktor Store")
    }

    @Test
    fun `GET api products returns seeded list`() = testApplication {
        configureTestApp()

        val response = client.get("/api/products") { accept(ContentType.Application.Json) }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Coffee Mug") || body.contains("T-shirt"))
    }

    @Test
    fun `GET api product by id returns product or 400 if unknown`() = testApplication {
        configureTestApp()

        val ok = client.get("/api/products/1") { accept(ContentType.Application.Json) }
        assertEquals(HttpStatusCode.OK, ok.status)
        assertContains(ok.bodyAsText(), "Coffee Mug")

        val bad = client.get("/api/products/99999") { accept(ContentType.Application.Json) }
        assertEquals(HttpStatusCode.BadRequest, bad.status)
    }

    @Test
    fun `POST login invalid credentials yields 401`() = testApplication {
        configureTestApp()

        val response = client.post("/login") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(listOf("username" to "admin", "password" to "wrong").formUrlEncode())
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST login valid credentials redirects to welcome and sets session`() = testApplication {
        configureTestApp()

        val client = cookieClient(followRedirects = false)

        val loginResponse = client.post("/login") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(listOf("username" to "admin", "password" to "pw").formUrlEncode())
        }
        assertEquals(HttpStatusCode.Found, loginResponse.status)
        assertEquals("/welcome", loginResponse.headers[HttpHeaders.Location])

        val welcome = client.get("/welcome")
        assertEquals(HttpStatusCode.OK, welcome.status)
        assertContains(welcome.bodyAsText(), "Welcome, admin!")
    }

    @Test
    fun `GET welcome without session redirects to login`() = testApplication {
        configureTestApp()

        val client = noRedirectClient()
        val response = client.get("/welcome")
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/login", response.headers[HttpHeaders.Location])
    }

    @Test
    fun `logout clears session and redirects to login`() = testApplication {
        configureTestApp()

        val client = cookieClient(followRedirects = false)
        // First log in to establish a session
        client.post("/login") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(listOf("username" to "admin", "password" to "pw").formUrlEncode())
        }

        val logout = client.get("/logout")
        assertEquals(HttpStatusCode.Found, logout.status)
        assertEquals("/login", logout.headers[HttpHeaders.Location])

        // Now welcome should redirect to login
        val welcome = client.get("/welcome")
        assertEquals(HttpStatusCode.Found, welcome.status)
        assertEquals("/login", welcome.headers[HttpHeaders.Location])
    }

    @Test
    fun `POST products requires session and creates product when authenticated`() = testApplication {
        configureTestApp()

        // Without session -> redirect to /login
        val noRedirect = noRedirectClient()
        val noAuth = noRedirect.post("/products") {
            contentType(ContentType.Application.Json)
            setBody("""{"upc":9998,"name":"Test Product","description":"Desc","price":1.23}""")
        }
        assertEquals(HttpStatusCode.Found, noAuth.status)
        assertEquals("/login", noAuth.headers[HttpHeaders.Location])

        // Login to obtain session
        val cookieClient = cookieClient(followRedirects = false)
        cookieClient.post("/login") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(listOf("username" to "admin", "password" to "pw").formUrlEncode())
        }

        val newUpc = (System.currentTimeMillis() % 100000000).toInt() + 10000
        val created = cookieClient.post("/products") {
            contentType(ContentType.Application.Json)
            setBody("""{"upc":$newUpc,"name":"Another Product","description":"Another Desc","price":9.99}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        assertContains(created.bodyAsText(), "Added product")
    }
}
