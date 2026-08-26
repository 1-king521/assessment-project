package com.acme.assessment.task

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SecureTokenServiceTest {
    private val service = SecureTokenService()

    @Test
    fun `generates URL safe unique tokens and deterministic hashes`() {
        val first = service.generate()
        val second = service.generate()

        assertThat(first).hasSize(43).matches("[A-Za-z0-9_-]+")
        assertThat(second).isNotEqualTo(first)
        assertThat(service.hash(first)).hasSize(64).isEqualTo(service.hash(first))
        assertThat(service.hash(second)).isNotEqualTo(service.hash(first))
    }
}

