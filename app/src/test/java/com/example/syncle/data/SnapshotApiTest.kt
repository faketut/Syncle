package com.example.syncle.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SnapshotApiTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun `parses peer list with optional tableId`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"room":"r1","peers":[
                  {"userId":"u1","nickname":"A","color":"#fff","tableId":"t1","x":100,"y":200,"lastSeen":1},
                  {"userId":"u2","nickname":"B","color":"#000","tableId":null,"x":50.5,"y":60.5,"lastSeen":2}
                ]}""".trimIndent(),
            ),
        )
        val api = SnapshotApi(server.url("/").toString().trimEnd('/'), OkHttpClient())
        val peers = api.fetch("r1")
        assertEquals(2, peers.size)
        assertEquals("u1", peers[0].userId)
        assertEquals("t1", peers[0].tableId)
        assertEquals(100f, peers[0].x, 0.001f)
        assertEquals("u2", peers[1].userId)
        assertNull(peers[1].tableId)
        assertEquals(50.5f, peers[1].x, 0.001f)

        val req = server.takeRequest()
        assertEquals("/v1/rooms/r1/snapshot", req.path)
        assertEquals("GET", req.method)
    }

    @Test
    fun `returns empty on http error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val api = SnapshotApi(server.url("/").toString(), OkHttpClient())
        assertTrue(api.fetch("r1").isEmpty())
    }

    @Test
    fun `returns empty when backend url empty`() = runTest {
        val api = SnapshotApi("", OkHttpClient())
        assertTrue(api.fetch("r1").isEmpty())
    }
}
