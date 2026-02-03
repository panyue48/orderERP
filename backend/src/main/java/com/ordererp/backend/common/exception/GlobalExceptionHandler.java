package com.ordererp.backend.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.persistence.OptimisticLockException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Make backend errors easier to diagnose from the frontend (especially for blob downloads like Excel).
 * We keep the payload small but always include a human-readable "message".
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException e, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body(HttpStatus.FORBIDDEN, e.getMessage(), req));
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(Exception e, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(HttpStatus.CONFLICT, "conflict, please retry", req));
    }

    @ExceptionHandler({IllegalArgumentException.class, BindException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception e, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body(HttpStatus.BAD_REQUEST, e.getMessage(), req));
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<Map<String, Object>> handleErrorResponseException(ErrorResponseException e, HttpServletRequest req) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        String msg = null;
        try {
            if (e.getBody() != null) msg = e.getBody().getDetail();
        } catch (Exception ignored) {
            // ignore
        }
        if (msg == null || msg.isBlank()) msg = e.getMessage();
        return ResponseEntity.status(status).body(body(status, msg, req));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnhandled(Exception e, HttpServletRequest req) {
        // Keep stacktrace in backend logs; frontend gets a stable JSON "message".
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), req));
    }

    private static Map<String, Object> body(HttpStatus status, String message, HttpServletRequest req) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", OffsetDateTime.now().toString());
        m.put("status", status.value());
        m.put("error", status.getReasonPhrase());
        m.put("message", message != null ? message : status.getReasonPhrase());
        m.put("path", req.getRequestURI());
        return m;
    }
}
