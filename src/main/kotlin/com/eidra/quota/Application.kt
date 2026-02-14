package com.eidra.quota

import com.eidra.quota.api.configureUsageRoutes
import io.ktor.server.netty.EngineMain

import com.eidra.quota.config.QuotaConfiguration
import com.eidra.quota.storage.InMemoryUsageRepository
import com.eidra.quota.service.QuotaService
import com.eidra.quota.time.SystemClock
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
        })
    }
    val clock = SystemClock()
    val repo = InMemoryUsageRepository()
    val config = QuotaConfiguration.quotas
    val service = QuotaService(config, repo, clock)
    configureUsageRoutes(service)
}
