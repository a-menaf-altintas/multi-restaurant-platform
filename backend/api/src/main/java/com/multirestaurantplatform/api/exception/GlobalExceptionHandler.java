package com.multirestaurantplatform.api.exception;

// ... other imports
import com.multirestaurantplatform.common.exception.BadRequestException; // Added
import com.multirestaurantplatform.common.exception.ConflictException; // Added
import com.multirestaurantplatform.common.exception.ResourceNotFoundException; // Added
import com.multirestaurantplatform.api.dto.error.ErrorResponse; // Added

import jakarta.servlet.http.HttpServletRequest; // Added if not present, or use WebRequest
import org.slf4j.Logger; // Added
import org.slf4j.LoggerFactory; // Added
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
// ...

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger customLogger = LoggerFactory.getLogger(GlobalExceptionHandler.class); // Renamed to avoid conflict if 'logger' is in parent

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString()); // Consider LocalDateTime directly if ErrorResponse is adapted
        body.put("status", status.value());
        body.put("error", "Bad Request"); // Or HttpStatus.BAD_REQUEST.getReasonPhrase()

        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());
        body.put("errors", errors); // This "errors" field is specific to validation

        String path = "";
        if (request != null && request.getDescription(false) != null) {
            path = request.getDescription(false).replace("uri=", "");
            body.put("path", path);
        }
        customLogger.warn("MethodArgumentNotValidException: {} errors for path {}: {}", ex.getBindingResult().getErrorCount(), path, errors);
        return new ResponseEntity<>(body, headers, status);
    }

    // --- New Handlers for Custom Exceptions ---

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) { // Using HttpServletRequest for simplicity here
        customLogger.warn("ResourceNotFoundException: {} at path {}", ex.getMessage(), request.getRequestURI());
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI());
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequestException(
            BadRequestException ex, HttpServletRequest request) {
        customLogger.warn("BadRequestException: {} at path {}", ex.getMessage(), request.getRequestURI());
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflictException(
            ConflictException ex, HttpServletRequest request) {
        customLogger.warn("ConflictException: {} at path {}", ex.getMessage(), request.getRequestURI());
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI());
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {
        customLogger.warn("AccessDeniedException: {} for path {}", ex.getMessage(), request.getRequestURI()); // Log as WARN or INFO
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "Access Denied: You do not have the necessary permissions to access this resource.", // Or ex.getMessage() if you prefer
                request.getRequestURI());
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    // --- Fallback Handler ---
    // This handles any other exceptions not specifically caught above or by ResponseEntityExceptionHandler
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        // Avoid logging known Spring Security exceptions like AccessDeniedException twice if already handled by Spring Security
        // Or if you have specific handlers for them
        customLogger.error("Unhandled Exception: {} at path {}", ex.getMessage(), request.getRequestURI(), ex);
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An unexpected internal server error occurred.", // Generic message to client
                request.getRequestURI());
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}