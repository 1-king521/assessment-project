package com.acme.assessment.service

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import com.acme.assessment.config.AppProperties
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

data class IssuedToken(val value: String, val expiresAt: Instant)

@Service
class JwtService(
    private val encoder: JwtEncoder,
    private val properties: AppProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun issue(user: LoginUser): IssuedToken {
        val now = clock.instant()
        //获取过期时间
        val expiresAt = now.plus(properties.jwt.ttl)
        //构建jwt载荷
        val claims = JwtClaimsSet.builder()
            .issuer(properties.jwt.issuer)
            .issuedAt(now)
            .expiresAt(expiresAt)
            .subject(user.username)
            .claim("uid", user.id)
            .claim("name", user.realName)
            .claim("roles", listOf(user.role))
            .apply { user.departmentId?.let { claim("departmentId", it) } }
            .build()
        //jwt请求头
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        //封装token和过期时间
        return IssuedToken(encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue, expiresAt)
    }
}

