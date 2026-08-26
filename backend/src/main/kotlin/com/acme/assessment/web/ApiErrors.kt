package com.acme.assessment.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

open class BusinessException(
    val code: String,
    override val message: String,
    val status: HttpStatus = HttpStatus.BAD_REQUEST,
) : RuntimeException(message)

class NotFoundException(resource: String) :
    BusinessException("RESOURCE_NOT_FOUND", "$resource 不存在", HttpStatus.NOT_FOUND)

class ConflictException(code: String, message: String) :
    BusinessException(code, message, HttpStatus.CONFLICT)

data class ApiError(
    val timestamp: Instant = Instant.now(),
    val status: Int,
    val code: String,
    val message: String,
    val path: String,
    val fieldErrors: Map<String, String> = emptyMap(),
)

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(BusinessException::class)
    fun business(exception: BusinessException, request: HttpServletRequest): ResponseEntity<ApiError> =
        ResponseEntity.status(exception.status).body(
            ApiError(
                status = exception.status.value(),
                code = exception.code,
                message = exception.message,
                path = request.requestURI,
            ),
        )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(exception: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ApiError> {
        val errors = exception.bindingResult.allErrors.associate {
            val field = (it as? FieldError)?.field ?: it.objectName
            field to (it.defaultMessage ?: "参数不合法")
        }
        return ResponseEntity.badRequest().body(
            ApiError(
                status = HttpStatus.BAD_REQUEST.value(),
                code = "VALIDATION_FAILED",
                message = "请求参数校验失败",
                path = request.requestURI,
                fieldErrors = errors,
            ),
        )
    }
}

