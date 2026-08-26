package com.acme.assessment.auth

import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class LoginRequest(
    @field:NotBlank(message = "用户名不能为空")
    val username: String,
    @field:NotBlank(message = "密码不能为空")
    val password: String,
)

data class LoginResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresAt: Instant,
    val user: LoginUser,
)

data class LoginUser(
    val id: Long,
    val username: String,
    val realName: String,
    val role: String,
    val departmentId: Long?,
)

data class CurrentUser(
    val id: Long,
    val username: String,
    val realName: String,
    val role: String,
    val departmentId: Long?,
) {
    fun isAnyRole(vararg allowed: String): Boolean = role in allowed
}

