package com.acme.assessment.controller

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.core.io.Resource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.nio.charset.StandardCharsets

@RestController
@RequestMapping("/api/review-assignments")
@PreAuthorize("hasRole('REVIEWER')")
class ReviewController(private val service: ReviewService) {
    @GetMapping
    fun listMine() = service.listMine()

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long) = service.detail(id)

    @GetMapping("/{id}/files/{fileId}")
    fun file(@PathVariable id: Long, @PathVariable fileId: Long): ResponseEntity<Resource> {
        val content = service.fileContent(id, fileId)
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

    @PostMapping("/{id}/start")
    fun start(@PathVariable id: Long) = service.start(id)

    @PostMapping("/{id}/submit")
    fun submit(@PathVariable id: Long, @Valid @RequestBody request: SubmitReviewRequest) = service.submit(id, request)
}
