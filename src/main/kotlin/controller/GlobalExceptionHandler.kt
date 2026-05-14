package cvut.fit.energyStats.controller

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException::class)
    fun illegalArgumentException(e: IllegalArgumentException): ResponseEntity<String> = ResponseEntity(e.message, HttpStatus.BAD_REQUEST)

    @ExceptionHandler(ResponseStatusException::class)
    fun responseStatusException(e: ResponseStatusException): ResponseEntity<String> = ResponseEntity(e.message, HttpStatus.CONFLICT)

    @ExceptionHandler(BadCredentialsException::class)
    fun badCredentialsException(e: BadCredentialsException): ResponseEntity<String> = ResponseEntity(e.message, HttpStatus.UNAUTHORIZED)

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(e: AccessDeniedException): ResponseEntity<String> = ResponseEntity(e.message, HttpStatus.FORBIDDEN)

    @ExceptionHandler(AuthorizationDeniedException::class)
    fun handleAuthorizationDenied(e: AuthorizationDeniedException): ResponseEntity<String> = ResponseEntity(e.message, HttpStatus.FORBIDDEN)

    @ExceptionHandler(Exception::class)
    fun exception(e: Exception): ResponseEntity<String> = ResponseEntity(e.message, HttpStatus.INTERNAL_SERVER_ERROR)
}
