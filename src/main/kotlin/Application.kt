package com.github.evp2

import com.github.evp2.plugins.configureAuthentication
import com.github.evp2.plugins.configureMonitoring
import com.github.evp2.plugins.configureRouting
import com.github.evp2.plugins.configureSerialization
import com.github.evp2.plugins.configureSessions
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureAuthentication()
    configureSessions()
    configureMonitoring()
    configureSerialization()
    configureRouting()
}
