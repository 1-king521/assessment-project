package com.acme.assessment.review

import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/review-assignments")
@PreAuthorize("hasRole('REVIEWER')")
class ReviewController(private val service: ReviewService) {
    @GetMapping
    fun listMine() = service.listMine()

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long) = service.detail(id)

    @PostMapping("/{id}/start")
    fun start(@PathVariable id: Long) = service.start(id)

    @PostMapping("/{id}/submit")
    fun submit(@PathVariable id: Long, @Valid @RequestBody request: SubmitReviewRequest) = service.submit(id, request)
}

