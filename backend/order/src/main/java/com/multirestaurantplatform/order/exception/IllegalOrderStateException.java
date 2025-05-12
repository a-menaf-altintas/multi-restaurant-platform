package com.multirestaurantplatform.order.exception;

public class IllegalOrderStateException extends RuntimeException {
    public IllegalOrderStateException(String message) {
        super(message);
    }

    public IllegalOrderStateException(String message, Throwable cause) {
        super(message, cause);
    }
}