package com.acme.assessment.config

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import com.acme.assessment.web.ApiError
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Instant
import javax.crypto.spec.SecretKeySpec

@Configuration
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtAuthenticationConverter: Converter<Jwt, out AbstractAuthenticationToken>,
    ): SecurityFilterChain = http
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests {
            it.requestMatchers("/actuator/health", "/api/auth/login", "/api/auth/register", "/api/auth/dingtalk-profile").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .anyRequest().authenticated()
        }
        .exceptionHandling {
            it.authenticationEntryPoint(JsonAuthenticationEntryPoint())
            it.accessDeniedHandler(JsonAccessDeniedHandler())
        }
        .oauth2ResourceServer {
            it.jwt { jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter) }
        }
        .httpBasic { it.disable() }
        .build()

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun jwtEncoder(properties: AppProperties): JwtEncoder {
        val key = SecretKeySpec(properties.jwt.secret.toByteArray(), "HmacSHA256")
        return NimbusJwtEncoder(ImmutableSecret(key))
    }

    @Bean
    fun jwtDecoder(properties: AppProperties): JwtDecoder {
        val key = SecretKeySpec(properties.jwt.secret.toByteArray(), "HmacSHA256")
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build()
    }

    @Bean
    fun jwtAuthenticationConverter(): Converter<Jwt, out AbstractAuthenticationToken> {
        val authorities = JwtGrantedAuthoritiesConverter().apply {
            setAuthoritiesClaimName("roles")
            setAuthorityPrefix("ROLE_")
        }
        return JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter(authorities)
            setPrincipalClaimName("sub")
        }
    }
}

private class JsonAuthenticationEntryPoint : AuthenticationEntryPoint {
    private val mapper = ObjectMapper().findAndRegisterModules()

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: org.springframework.security.core.AuthenticationException,
    ) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = "application/json;charset=UTF-8"
        mapper.writeValue(response.outputStream, ApiError(
            timestamp = Instant.now(),
            status = 401,
            code = "UNAUTHENTICATED",
            message = "用户未登录或登录已失效",
            path = request.requestURI,
        ))
    }
}

private class JsonAccessDeniedHandler : AccessDeniedHandler {
    private val mapper = ObjectMapper().findAndRegisterModules()

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: org.springframework.security.access.AccessDeniedException,
    ) {
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = "application/json;charset=UTF-8"
        mapper.writeValue(response.outputStream, ApiError(
            timestamp = Instant.now(),
            status = 403,
            code = "FORBIDDEN",
            message = "当前用户无权访问该资源",
            path = request.requestURI,
        ))
    }
}
