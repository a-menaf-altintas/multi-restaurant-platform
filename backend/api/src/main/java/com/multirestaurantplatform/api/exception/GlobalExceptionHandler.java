package com.multirestaurantplatform.api.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", "Bad Request");

        // Get all field errors
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());

        body.put("errors", errors);
        // If you prefer a map of field to error message:
        // Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
        //        .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage,
        //                (existingValue, newValue) -> existingValue + "; " + newValue)); // Handle duplicate fields if any
        // body.put("fieldErrors", fieldErrors);


        if (request != null && request.getDescription(false) != null) {
            body.put("path", request.getDescription(false).replace("uri=", ""));
        }

        return new ResponseEntity<>(body, headers, status);
    }

    // You can add more @ExceptionHandler methods here for other exceptions
    // For example, handling your custom UserAlreadyExistsException, etc.
}