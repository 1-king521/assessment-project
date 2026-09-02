package com.acme.assessment.controller

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import com.acme.assessment.entity.TaskStatus
import jakarta.validation.Valid
import org.springframework.core.io.Resource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets

@RestController
@RequestMapping("/api/assessment-tasks")
@PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'ADMIN')")
class TaskManagementController(private val service: TaskManagementService) {
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
        @RequestParam(required = false) status: TaskStatus?,
        @RequestParam(required = false) keyword: String?,
    ): TaskPageResponse = service.list(page, pageSize, status, keyword)

    @GetMapping("/statistics")
    fun statistics(): TaskStatisticsResponse = service.statistics()

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): TaskDetailResponse = service.detail(id)

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
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.filename(content.fileName, StandardCharsets.UTF_8).build().toString())
            .body(content.resource)
    }

    @PostMapping("/{id}/reviewers")
    fun assignReviewers(@PathVariable id: Long, @Valid @RequestBody request: AssignReviewersRequest): TaskDetailResponse = service.assignReviewers(id, request)

    @PostMapping("/{id}/revoke")
    fun revoke(@PathVariable id: Long): TaskActionResponse = service.revoke(id)

    @PostMapping("/{id}/extend")
    fun extend(@PathVariable id: Long, @Valid @RequestBody request: ExtendTaskRequest): TaskActionResponse = service.extend(id, request)

    @PostMapping("/{id}/archive")
    fun archive(@PathVariable id: Long): TaskActionResponse = service.archive(id)
}
