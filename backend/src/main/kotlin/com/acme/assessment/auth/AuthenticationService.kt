package com.acme.assessment.auth

import com.acme.assessment.domain.UserStatus
import com.acme.assessment.repository.RoleRepository
import com.acme.assessment.repository.UserRepository
import com.acme.assessment.web.BusinessException
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class AuthenticationService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional
    fun login(request: LoginRequest): LoginResponse {
        val user = userRepository.findByUsername(request.username.trim())
            ?: throw invalidCredentials()
        if (user.status != UserStatus.ACTIVE || !passwordEncoder.matches(request.password, user.passwordHash)) {
            throw invalidCredentials()
        }
        val role = roleRepository.findById(user.roleId).orElseThrow { invalidCredentials() }
        if (role.status != "ENABLED") throw invalidCredentials()

        user.lastLoginAt = clock.instant()
        val loginUser = LoginUser(
            id = requireNotNull(user.id),
            username = user.username,
            realName = user.realName,
            role = role.roleCode,
            departmentId = user.departmentId,
        )
        val token = jwtService.issue(loginUser)
        return LoginResponse(token.value, expiresAt = token.expiresAt, user = loginUser)
    }

    @Transactional(readOnly = true)
    fun currentUser(): CurrentUser {
        val authentication = SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken
            ?: throw BusinessException("UNAUTHENTICATED", "用户未登录", HttpStatus.UNAUTHORIZED)
        val user = userRepository.findByUsername(authentication.name)
            ?: throw BusinessException("UNAUTHENTICATED", "登录用户不存在", HttpStatus.UNAUTHORIZED)
        if (user.status != UserStatus.ACTIVE) {
            throw BusinessException("ACCOUNT_UNAVAILABLE", "账号不可用", HttpStatus.FORBIDDEN)
        }
        val role = roleRepository.findById(user.roleId).orElseThrow {
            BusinessException("ACCOUNT_UNAVAILABLE", "用户角色不存在", HttpStatus.FORBIDDEN)
        }
        return CurrentUser(
            id = requireNotNull(user.id),
            username = user.username,
            realName = user.realName,
            role = role.roleCode,
            departmentId = user.departmentId,
        )
    }

    private fun invalidCredentials() =
        BusinessException("INVALID_CREDENTIALS", "用户名或密码错误", HttpStatus.UNAUTHORIZED)
}

