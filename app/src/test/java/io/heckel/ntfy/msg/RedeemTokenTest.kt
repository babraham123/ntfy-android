package io.heckel.ntfy.msg

import com.google.gson.JsonParser
import io.heckel.ntfy.db.CustomHeader
import io.heckel.ntfy.db.User
import io.heckel.ntfy.util.HttpUtil
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class RedeemTokenTest {
    private lateinit var server: MockWebServer
    private lateinit var baseUrl: String
    private val redeemer = TokenRedeemer(OkHttpClient())

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        baseUrl = server.url("/").toString().trimEnd('/')
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `sends unauthenticated JSON POST and returns token`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"token":"tk_abc","label":"Pixel 8 (ntfy Android)","last_access":1700000000,"last_origin":"1.2.3.4","expires":0,"username":"phil"}"""
        ))

        val redeemed = redeemer.redeem(baseUrl, "code123", "Pixel 8 (ntfy Android)", listOf(CustomHeader(baseUrl, "X-Proxy", "yes")))

        assertEquals(TokenRedeemer.RedeemedToken(token = "tk_abc", username = "phil"), redeemed)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/account/token/redeem", request.path)
        assertNull(request.getHeader("Authorization"))
        assertEquals("yes", request.getHeader("X-Proxy"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("code123", body["code"].asString)
        assertEquals("Pixel 8 (ntfy Android)", body["label"].asString)
    }

    @Test
    fun `invalid code throws ApiException with code 40060`() {
        server.enqueue(MockResponse().setResponseCode(400).setBody(
            """{"code":40060,"http":400,"error":"invalid request: enrollment code invalid, already used or expired"}"""
        ))
        try {
            redeemer.redeem(baseUrl, "used", "label")
            fail("Expected ApiException")
        } catch (e: ApiService.ApiException) {
            assertEquals(TokenRedeemer.ERROR_CODE_INVALID_CODE, e.code)
        }
    }

    @Test(expected = TokenRedeemer.ServerUnsupportedException::class)
    fun `404 means server is too old`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"code":40401,"http":404,"error":"page not found"}"""))
        redeemer.redeem(baseUrl, "code123", "label")
    }

    @Test
    fun `response without token is rejected`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"username":"phil"}"""))
        try {
            redeemer.redeem(baseUrl, "code123", "label")
            fail("Expected exception")
        } catch (e: Exception) {
            assertTrue(e !is ApiService.ApiException)
        }
    }

    @Test
    fun `token user is sent as bearer, password user as basic`() {
        val bearer = HttpUtil.requestBuilder("$baseUrl/t", User(baseUrl, "phil", "", "tk_abc")).build()
        assertEquals("Bearer tk_abc", bearer.header("Authorization"))
        val basic = HttpUtil.requestBuilder("$baseUrl/t", User(baseUrl, "phil", "secret")).build()
        assertEquals("Basic cGhpbDpzZWNyZXQ=", basic.header("Authorization"))
    }
}
