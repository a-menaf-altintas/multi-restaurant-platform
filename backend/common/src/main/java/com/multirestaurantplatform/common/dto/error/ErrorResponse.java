package com.multirestaurantplatform.common.dto.error;

import java.time.LocalDateTime;

public record ErrorResponse(
    LocalDateTime timestamp,
    int status,
    String error, // Short error description e.g., "Not Found", "Bad Request"
    String message, // Detailed error message from the exception
    String path // The request path
) {
}