package com.acme.assessment.service

import com.acme.assessment.config.AppProperties
import com.acme.assessment.config.BootstrapProperties
import com.acme.assessment.config.JwtProperties
import com.acme.assessment.dto.PresignFileRequest
import com.acme.assessment.entity.AssessmentFile
import com.acme.assessment.repository.AssessmentFileRepository
import com.acme.assessment.repository.OperationLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Duration

class FileStorageServiceTest {
    private val fileRepository = mock<AssessmentFileRepository>()
    private val operationLogRepository = mock<OperationLogRepository>()
    private lateinit var service: FileStorageService

    @BeforeEach
    fun setUp() {
        val properties = AppProperties(
            jwt = JwtProperties("test", "test-secret", Duration.ofHours(1)),
            publicBaseUrl = "http://localhost",
            bootstrap = BootstrapProperties("admin", "password"),
        )
        service = FileStorageService(fileRepository, operationLogRepository, properties, Clock.systemUTC())
        whenever(fileRepository.save(any<AssessmentFile>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `accepts files with any extension`() {
        val file = service.createRecord(42, request("portfolio.custom"))

        assertThat(file.fileName).isEqualTo("portfolio.custom")
        assertThat(file.fileExtension).isEqualTo("custom")
        assertThat(file.objectKey).startsWith("42/").endsWith(".custom")
    }

    @Test
    fun `accepts files without an extension`() {
        val file = service.createRecord(42, request("README"))

        assertThat(file.fileName).isEqualTo("README")
        assertThat(file.fileExtension).isEmpty()
        assertThat(file.objectKey).startsWith("42/")
        assertThat(file.objectKey.substringAfterLast('/')).doesNotContain(".")
    }

    private fun request(fileName: String) = PresignFileRequest(
        questionId = "question-1",
        fileName = fileName,
        contentType = "application/octet-stream",
        sizeBytes = 1024,
    )
}
