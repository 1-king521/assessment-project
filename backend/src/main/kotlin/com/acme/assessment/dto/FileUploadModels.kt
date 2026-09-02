package com.acme.assessment.dto

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import java.time.Instant

data class PresignFileRequest(
    @field:NotBlank val questionId: String,
    @field:NotBlank val fileName: String,
    @field:NotBlank val contentType: String,
    @field:Positive val sizeBytes: Long,
)

data class PresignFileResponse(
    val fileId: Long,
    val objectKey: String,
    val uploadUrl: String,
    val expiresAt: Instant,
    val uploadStatus: String,
)

data class CompleteFileRequest(
    @field:NotBlank val checksum: String,
)

data class FileUploadResponse(
    val fileId: Long,
    val fileName: String,
    val sizeBytes: Long,
    val uploadStatus: String,
    val completedAt: Instant?,
)

