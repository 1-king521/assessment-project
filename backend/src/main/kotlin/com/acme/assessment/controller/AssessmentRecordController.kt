package com.acme.assessment.controller

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import com.acme.assessment.entity.ReviewConclusion
import org.springframework.core.io.Resource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets

@RestController
@RequestMapping("/api/assessment-records")
@PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'ADMIN')")
class AssessmentRecordController(private val service: TaskManagementService) {
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) positionId: Long?,
        @RequestParam(required = false) conclusion: ReviewConclusion?,
    ) = service.listRecords(page, pageSize, keyword, positionId, conclusion)

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long) = service.recordDetail(id)

    @GetMapping("/{id}/files/{fileId}")
    fun file(@PathVariable id: Long, @PathVariable fileId: Long): ResponseEntity<Resource> {
        val content = service.recordFileContent(id, fileId)
        val mediaType = try {
            MediaType.parseMediaType(content.contentType)
        } catch (_: IllegalArgumentException) {
            MediaType.APPLICATION_OCTET_STREAM
        }
        val disposition = if (mediaType.type.equals("image", ignoreCase = true)) {
            ContentDisposition.inline()
        } else {
            ContentDisposition.attachment()
        }
        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                disposition.filename(content.fileName, StandardCharsets.UTF_8).build().toString(),
            )
            .body(content.resource)
    }
}
