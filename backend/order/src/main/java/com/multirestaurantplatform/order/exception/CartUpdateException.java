package com.multirestaurantplatform.order.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class CartUpdateException extends RuntimeException {
    public CartUpdateException(String message) {
        super(message);
    }
}