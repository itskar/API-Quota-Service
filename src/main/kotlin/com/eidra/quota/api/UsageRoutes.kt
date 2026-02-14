package com.eidra.quota.api

import com.eidra.quota.service.QuotaDecision
import com.eidra.quota.service.QuotaService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.eidra.quota.api.UsageRoutes")

private const val RATE_LIMIT_LIMIT = "X-RateLimit-Limit"
private const val RATE_LIMIT_REMAINING = "X-RateLimit-Remaining"
private const val RATE_LIMIT_RESET = "X-RateLimit-Reset"

/**
 * Mask API key for logging so we do not log raw keys.
 */
fun maskApiKey(key: String): String {
    if (key.length <= 4) return "****"
    return "***${key.takeLast(4)}"
}

fun Application.configureUsageRoutes(service: QuotaService) {
    routing {
        post(ApiRoutes.CONSUME) {
            val apiKey = call.request.header("X-API-Key")
            if (apiKey.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing X-API-Key header")
                return@post
            }
            if (!service.hasQuota(apiKey)) {
                logger.info("reject key=unknown (masked={}) reason=forbidden", maskApiKey(apiKey))
                call.respond(HttpStatusCode.Forbidden, "Invalid API key")
                return@post
            }
            val body = try {
                call.receive<ConsumeRequest>()
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid JSON or missing body")
                return@post
            }
            if (body.units <= 0L) {
                call.respond(HttpStatusCode.BadRequest, "Units must be positive")
                return@post
            }
            val decision = try {
                service.consume(apiKey, body.units)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.Forbidden, "Invalid API key")
                return@post
            }
            val status = QuotaStatusResponse(
                limit = decision.limit,
                remaining = decision.remaining,
                resetAtEpochSeconds = decision.resetAtEpochSeconds
            )
            call.response.headers.append(RATE_LIMIT_LIMIT, decision.limit.toString())
            call.response.headers.append(RATE_LIMIT_REMAINING, decision.remaining.toString())
            call.response.headers.append(RATE_LIMIT_RESET, decision.resetAtEpochSeconds.toString())
            when (decision) {
                is QuotaDecision.Accepted -> {
                    logger.info("accept key={} remaining={} limit={}", maskApiKey(apiKey), decision.remaining, decision.limit)
                    call.respond(HttpStatusCode.OK, status)
                }
                is QuotaDecision.Rejected -> {
                    logger.info("reject key={} reason=quota_exceeded remaining={} limit={}", maskApiKey(apiKey), decision.remaining, decision.limit)
                    call.respond(HttpStatusCode.TooManyRequests, status)
                }
            }
        }
    }
}
