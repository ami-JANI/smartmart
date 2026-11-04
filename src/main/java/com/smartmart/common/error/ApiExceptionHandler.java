package com.smartmart.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

/**
 * Turns every exception from a REST controller into an {@link ApiError}
 * (GUIDELINES.md section 6). Responses never carry stack traces or SQL: an
 * unexpected exception is logged and answered with a fixed message.
 * Thymeleaf page controllers are not covered; their errors render the
 * templates in {@code templates/error}.
 */
@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BindException.class)
    ResponseEntity<ApiError> invalidBody(BindException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Invalid request");
        return error(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> invalidParameter(ConstraintViolationException ex, HttpServletRequest request) {
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("Invalid request");
        return error(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, TypeMismatchException.class})
    ResponseEntity<ApiError> unreadable(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "The request has a missing or badly formed value", request);
    }

    /** Left to Spring Security, which answers 401 or 403. */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    void security(RuntimeException ex) {
        throw ex;
    }

    /**
     * Spring MVC's own errors (a missing parameter, an unsupported
     * media type) keep their status with its reason phrase as the message.
     * Exceptions marked with {@code @ResponseStatus} keep that status and
     * their message. Anything else is a 500.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> other(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse spring) {
            HttpStatusCode status = spring.getStatusCode();
            return error(status, HttpStatus.valueOf(status.value()).getReasonPhrase(), request);
        }
        ResponseStatus marked = AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);
        if (marked != null) {
            return error(marked.code(), ex.getMessage(), request);
        }
        log.error("Unexpected error on {}", request.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.", request);
    }

    private ResponseEntity<ApiError> error(HttpStatusCode status, String message, HttpServletRequest request) {
        String reason = HttpStatus.valueOf(status.value()).getReasonPhrase();
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), reason, message, request.getRequestURI()));
    }
}
