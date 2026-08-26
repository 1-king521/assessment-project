package com.acme.assessment.publicapi

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.time.Instant

data class SendPhoneCodeRequest(
    @field:NotBlank val phone: String,
)

data class SendPhoneCodeResponse(
    val expiresAt: Instant,
    val debugCode: String? = null,
)

data class VerifyPhoneCodeRequest(
    @field:NotBlank val phone: String,
    @field:NotBlank @field:Pattern(regexp = "[0-9]{6}") val code: String,
)

data class VerifyPhoneCodeResponse(
    val verified: Boolean = true,
    val expiresAt: Instant,
)

