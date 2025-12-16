package com.github.evp2

import com.github.evp2.plugins.LoginRateLimiter
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class LoginRateLimiterIntTest {

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
    fun `bans IP after three invalid login attempts`() = testApplication {
        // ensure clean limiter state for this test run
        LoginRateLimiter.resetAll()
        configureTestApp()

        val client = noRedirectClient()

        // 1st invalid -> redirect to error page
        val r1 = client.post("/login") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(listOf("username" to "admin", "password" to "wrong1").formUrlEncode())
        }
        assertEquals(HttpStatusCode.Found, r1.status)
        assertEquals("/login?error=1", r1.headers[HttpHeaders.Location])

        // 2nd invalid -> redirect to error page
        val r2 = client.post("/login") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(listOf("username" to "admin", "password" to "wrong2").formUrlEncode())
        }
        assertEquals(HttpStatusCode.Found, r2.status)
        assertEquals("/login?error=1", r2.headers[HttpHeaders.Location])

        // 3rd invalid -> should trigger ban and return 429
        val r3 = client.post("/login") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(listOf("username" to "admin", "password" to "wrong3").formUrlEncode())
        }
        assertEquals(HttpStatusCode.TooManyRequests, r3.status)
        assertContains(r3.bodyAsText(), "Too many failed login attempts")
    }

    @Test
    fun `subsequent invalid attempts while banned still return 429`() = testApplication {
        LoginRateLimiter.resetAll()
        configureTestApp()

        val client = noRedirectClient()

        // Trigger the ban
        repeat(3) { idx ->
            val resp = client.post("/login") {
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody(listOf("username" to "admin", "password" to "bad$idx").formUrlEncode())
            }
            if (idx < 2) {
                assertEquals(HttpStatusCode.Found, resp.status)
                assertEquals("/login?error=1", resp.headers[HttpHeaders.Location])
            } else {
                assertEquals(HttpStatusCode.TooManyRequests, resp.status)
            }
        }

        // Another invalid try while banned -> 429
        val bannedTry = client.post("/login") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(listOf("username" to "admin", "password" to "stillbad").formUrlEncode())
        }
        assertEquals(HttpStatusCode.TooManyRequests, bannedTry.status)
        assertContains(bannedTry.bodyAsText(), "Too many failed login attempts")
    }

    @Test
    fun `valid login still allowed after failures and ban persists for subsequent failures`() = testApplication {
        LoginRateLimiter.resetAll()
        configureTestApp()

        val client = cookieClient(followRedirects = false)

        // Two failures (below ban threshold)
        repeat(2) {
            val resp = client.post("/login") {
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody(listOf("username" to "admin", "password" to "nope$it").formUrlEncode())
            }
            assertEquals(HttpStatusCode.Found, resp.status)
            assertEquals("/login?error=1", resp.headers[HttpHeaders.Location])
        }

        // Successful login should be allowed
        val ok = client.post("/login") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(listOf("username" to "admin", "password" to "pw").formUrlEncode())
        }
        assertEquals(HttpStatusCode.Found, ok.status)
        assertEquals("/welcome", ok.headers[HttpHeaders.Location])

        // Now trigger the ban with three fresh failures
        val fail1 = client.post("/login") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(listOf("username" to "admin", "password" to "bad1").formUrlEncode())
        }
        assertEquals(HttpStatusCode.Found, fail1.status)
        assertEquals("/login?error=1", fail1.headers[HttpHeaders.Location])

        val fail2 = client.post("/login") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(listOf("username" to "admin", "password" to "bad2").formUrlEncode())
        }
        assertEquals(HttpStatusCode.Found, fail2.status)
        assertEquals("/login?error=1", fail2.headers[HttpHeaders.Location])

        val banned = client.post("/login") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(listOf("username" to "admin", "password" to "bad3").formUrlEncode())
        }
        assertEquals(HttpStatusCode.TooManyRequests, banned.status)
        assertContains(banned.bodyAsText(), "Too many failed login attempts")

        // And remains banned for another invalid try
        val stillBanned = client.post("/login") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(listOf("username" to "admin", "password" to "bad4").formUrlEncode())
        }
        assertEquals(HttpStatusCode.TooManyRequests, stillBanned.status)
    }
}
