package com.acme.assessment.auth

import com.acme.assessment.dto.CurrentUser
import com.acme.assessment.dto.LoginRequest
import com.acme.assessment.dto.LoginResponse
import com.acme.assessment.dto.RegisterRequest
import com.acme.assessment.dto.RegistrationResponse
import com.acme.assessment.dto.DingTalkProfileRequest
import com.acme.assessment.dto.DingTalkProfileResponse
import com.acme.assessment.service.AuthenticationService
import com.acme.assessment.service.DingTalkUserService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authenticationService: AuthenticationService,
    private val dingTalkUserService: DingTalkUserService,
) {
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): LoginResponse = authenticationService.login(request)

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): RegistrationResponse =
        authenticationService.register(request)

    @PostMapping("/dingtalk-profile")
    fun dingtalkProfile(@Valid @RequestBody request: DingTalkProfileRequest): DingTalkProfileResponse =
        dingTalkUserService.lookupProfile(request.phone)

    @GetMapping("/me")
    fun me(): CurrentUser = authenticationService.currentUser()
}

