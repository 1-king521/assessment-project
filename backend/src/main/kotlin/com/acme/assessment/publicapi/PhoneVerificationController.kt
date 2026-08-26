package com.acme.assessment.publicapi

import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/public/assessments/{token}/phone")
class PhoneVerificationController(private val sessions: CandidateSessionService) {
    @PostMapping("/send-code")
    fun sendCode(@PathVariable token: String, @Valid @RequestBody request: SendPhoneCodeRequest): SendPhoneCodeResponse {
        val result = sessions.sendCode(token, request.phone)
        return SendPhoneCodeResponse(result.expiresAt, result.debugCode)
    }

    @PostMapping("/verify")
    fun verify(
        @PathVariable token: String,
        @Valid @RequestBody request: VerifyPhoneCodeRequest,
        response: HttpServletResponse,
    ): VerifyPhoneCodeResponse {
        val result = sessions.verifyCode(token, request.phone, request.code)
        response.addHeader(HttpHeaders.SET_COOKIE, result.cookie.toString())
        return VerifyPhoneCodeResponse(expiresAt = result.expiresAt)
    }
}
