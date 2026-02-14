package com.eidra.quota.api

import com.eidra.quota.config.QuotaConfiguration
import com.eidra.quota.domain.Quota
import com.eidra.quota.service.QuotaService
import com.eidra.quota.storage.InMemoryUsageRepository
import com.eidra.quota.time.FakeClock
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals

class UsageRoutesTest {

    private fun Application.testModule(clock: FakeClock, quotas: Map<String, Quota>) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        val repo = InMemoryUsageRepository()
        val service = QuotaService(quotas, repo, clock)
        configureUsageRoutes(service)
    }

    @Test
    fun `happy path - accepted consumes units`() = testApplication {
        val clock = FakeClock(Instant.EPOCH)
        val quotas = mapOf("client-a" to Quota(100L, Duration.ofHours(1)))
        application { testModule(clock, quotas) }
        val response = client.post(ApiRoutes.CONSUME) {
            header("X-API-Key", "client-a")
            contentType(ContentType.Application.Json)
            setBody("""{"units": 10}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("100", response.headers["X-RateLimit-Limit"])
        assertEquals("90", response.headers["X-RateLimit-Remaining"])
        val body = response.bodyAsText()
        assert(body.contains(""""remaining":90"""))
        assert(body.contains(""""limit":100"""))
        // Second request: remaining 80
        val response2 = client.post(ApiRoutes.CONSUME) {
            header("X-API-Key", "client-a")
            contentType(ContentType.Application.Json)
            setBody("""{"units": 10}""")
        }
        assertEquals(HttpStatusCode.OK, response2.status)
        assertEquals("80", response2.headers["X-RateLimit-Remaining"])
    }

    @Test
    fun `rejection - consume up to limit then 429`() = testApplication {
        val clock = FakeClock(Instant.EPOCH)
        val quotas = mapOf("client-a" to Quota(10L, Duration.ofHours(1)))
        application { testModule(clock, quotas) }
        repeat(10) {
            val r = client.post(ApiRoutes.CONSUME) {
                header("X-API-Key", "client-a")
                contentType(ContentType.Application.Json)
                setBody("""{"units": 1}""")
            }
            assertEquals(HttpStatusCode.OK, r.status, "request ${it + 1}")
        }
        val rejected = client.post(ApiRoutes.CONSUME) {
            header("X-API-Key", "client-a")
            contentType(ContentType.Application.Json)
            setBody("""{"units": 1}""")
        }
        assertEquals(HttpStatusCode.TooManyRequests, rejected.status)
        assertEquals("0", rejected.headers["X-RateLimit-Remaining"])
        assertEquals("10", rejected.headers["X-RateLimit-Limit"])
        // Next request still 429, remaining still 0 (no consume)
        val rejected2 = client.post(ApiRoutes.CONSUME) {
            header("X-API-Key", "client-a")
            contentType(ContentType.Application.Json)
            setBody("""{"units": 1}""")
        }
        assertEquals(HttpStatusCode.TooManyRequests, rejected2.status)
        assertEquals("0", rejected2.headers["X-RateLimit-Remaining"])
    }

    @Test
    fun `window reset - advance clock beyond window then accepted again`() = testApplication {
        val clock = FakeClock(Instant.EPOCH)
        val quotas = mapOf("client-a" to Quota(2L, Duration.ofHours(1)))
        application { testModule(clock, quotas) }
        client.post(ApiRoutes.CONSUME) {
            header("X-API-Key", "client-a")
            contentType(ContentType.Application.Json)
            setBody("""{"units": 2}""")
        }.also { assertEquals(HttpStatusCode.OK, it.status) }
        val over = client.post(ApiRoutes.CONSUME) {
            header("X-API-Key", "client-a")
            contentType(ContentType.Application.Json)
            setBody("""{"units": 1}""")
        }
        assertEquals(HttpStatusCode.TooManyRequests, over.status)
        clock.advanceBy(Duration.ofHours(1).plusSeconds(1))
        val afterReset = client.post(ApiRoutes.CONSUME) {
            header("X-API-Key", "client-a")
            contentType(ContentType.Application.Json)
            setBody("""{"units": 1}""")
        }
        assertEquals(HttpStatusCode.OK, afterReset.status)
        assertEquals("1", afterReset.headers["X-RateLimit-Remaining"])
    }

    @Test
    fun `concurrency - 100 requests limit 50 yields exactly 50 OK and 50 429`() = testApplication {
        val clock = FakeClock(Instant.EPOCH)
        val quotas = mapOf("concurrent-key" to Quota(50L, Duration.ofHours(1)))
        application { testModule(clock, quotas) }
        val results = runBlocking {
            coroutineScope {
                (1..100).map {
                    async {
                        client.post(ApiRoutes.CONSUME) {
                            header("X-API-Key", "concurrent-key")
                            contentType(ContentType.Application.Json)
                            setBody("""{"units": 1}""")
                        }.status
                    }
                }.awaitAll()
            }
        }
        val ok = results.count { it == HttpStatusCode.OK }
        val tooMany = results.count { it == HttpStatusCode.TooManyRequests }
        assertEquals(50, ok, "expected 50 OK, got $ok")
        assertEquals(50, tooMany, "expected 50 429, got $tooMany")
    }

    @Test
    fun `400 - missing X-API-Key`() = testApplication {
        application { testModule(FakeClock(), QuotaConfiguration.quotas) }
        val r = client.post(ApiRoutes.CONSUME) {
            contentType(ContentType.Application.Json)
            setBody("""{"units": 1}""")
        }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test
    fun `400 - units zero or negative`() = testApplication {
        application { testModule(FakeClock(), QuotaConfiguration.quotas) }
        val r = client.post(ApiRoutes.CONSUME) {
            header("X-API-Key", "client-a")
            contentType(ContentType.Application.Json)
            setBody("""{"units": 0}""")
        }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test
    fun `403 - unknown API key`() = testApplication {
        application { testModule(FakeClock(), QuotaConfiguration.quotas) }
        val r = client.post(ApiRoutes.CONSUME) {
            header("X-API-Key", "unknown-key")
            contentType(ContentType.Application.Json)
            setBody("""{"units": 1}""")
        }
        assertEquals(HttpStatusCode.Forbidden, r.status)
    }
}
