package com.acme.assessment.config

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("app")
data class AppProperties(
    val jwt: JwtProperties,
    val publicBaseUrl: String,
    val bootstrap: BootstrapProperties,
    val candidate: CandidateProperties = CandidateProperties(),
    val material: MaterialProperties = MaterialProperties(),
    val dingtalk: DingTalkProperties = DingTalkProperties(),
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
    val storagePath: String = "./data/uploads",
    val maxFileBytes: Long = 104857600,
    val allowedExtensions: Set<String> = setOf("jpg", "jpeg", "png", "gif", "pdf", "psd", "blend", "fbx", "obj", "zip", "rar"),
)

data class MaterialProperties(
    val storagePath: String = "./data/materials",
    val maxFileBytes: Long = 104857600,
    val allowedExtensions: Set<String> = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "tif", "tiff",
        "pdf", "txt", "md", "csv", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "zip", "rar", "7z",
        "mp3", "wav", "m4a", "mp4", "mov", "avi", "webm",
        "psd", "ai", "sketch", "fig", "blend", "fbx", "obj", "stl", "ma", "max",
    ),
)

data class DingTalkProperties(
    val enabled: Boolean = false,
    val clientId: String = "",
    val clientSecret: String = "",
    val robotCode: String = "",
    val apiBaseUrl: String = "https://api.dingtalk.com",
    /**
     * 老版服务端接口地址。新版 API 未提供“按手机号查 userId”的等价接口，
     * 该能力只存在于 oapi，因此单独保留一个 base url。
     */
    val oapiBaseUrl: String = "https://oapi.dingtalk.com",
    val accessTokenTtlSeconds: Long = 5400,
)
