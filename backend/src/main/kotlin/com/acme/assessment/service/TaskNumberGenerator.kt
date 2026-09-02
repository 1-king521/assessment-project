package com.acme.assessment.service

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Component
class TaskNumberGenerator(
    private val clock: Clock = Clock.systemUTC(),
) {
    private val random = SecureRandom()
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC)

    fun next(): String = "TEST${formatter.format(clock.instant())}${random.nextInt(10000).toString().padStart(4, '0')}"
}

