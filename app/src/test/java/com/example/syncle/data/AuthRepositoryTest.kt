package com.example.syncle.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchSession posts expected body and parses success`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"userId":"u-1","nickname":"Alice","color":"#fff","serverUrl":"ws://lk","token":"jwt.token.here","expiresAt":1}"""
                )
        )
        val repo = AuthRepository(
            backendUrl = server.url("/").toString().trimEnd('/'),
            client = OkHttpClient(),
        )

        val details = repo.fetchSession("dev-1", "Alice", "#fff", "room-x")

        assertEquals("u-1", details!!.userId)
        assertEquals("ws://lk", details.serverUrl)
        assertEquals("jwt.token.here", details.token)
        assertEquals("Alice", details.nickname)
        assertEquals("#fff", details.color)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/v1/sessions", req.path)
        val body = JSONObject(req.body.readUtf8())
        assertEquals("dev-1", body.getString("deviceId"))
        assertEquals("Alice", body.getString("nickname"))
        assertEquals("#fff", body.getString("color"))
        assertEquals("room-x", body.getString("room"))
    }

    @Test
    fun `fetchSession returns null on http error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
        val repo = AuthRepository(server.url("/").toString(), OkHttpClient())
        val details = repo.fetchSession("dev-2", "Bob", "#000")
        assertNull(details)
    }

    @Test
    fun `fetchSession returns null on empty backend url`() = runTest {
        val repo = AuthRepository(backendUrl = "", client = OkHttpClient())
        val details = repo.fetchSession("dev-3", "Carol", "#111")
        assertNull(details)
    }
}
