package com.multirestaurantplatform.security.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data // Lombok: Generates getters, setters, toString, equals, hashCode, and a constructor for all final fields.
public class LoginRequest {

    @NotBlank(message = "Username cannot be blank")
    private String username;

    @NotBlank(message = "Password cannot be blank")
    private String password;

    // No-args constructor (Lombok @Data might provide one, but explicit can be good)
    public LoginRequest() {
    }

    // All-args constructor (Lombok @Data will provide one for final fields, but this is explicit)
    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }
}
