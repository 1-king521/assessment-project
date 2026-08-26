package com.acme.assessment.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("app")
data class AppProperties(
    val jwt: JwtProperties,
    val publicBaseUrl: String,
    val bootstrap: BootstrapProperties,
    val candidate: CandidateProperties = CandidateProperties(),
)

data class JwtProperties(
    val issuer: String,
    val secret: String,
    val ttl: Duration,
)

data class BootstrapProperties(
    val adminUsername: String,
    val adminPassword: String,
)

data class CandidateProperties(
    val storeMode: String = "MEMORY",
    val codeTtlSeconds: Long = 300,
    val sessionTtlSeconds: Long = 7200,
    val cookieName: String = "candidate_session",
    val cookieSecure: Boolean = true,
    val debugCodeResponse: Boolean = true,
    val storagePath: String = "./data/uploads",
    val maxFileBytes: Long = 104857600,
    val allowedExtensions: Set<String> = setOf("jpg", "jpeg", "png", "gif", "pdf", "psd", "blend", "fbx", "obj", "zip", "rar"),
)
