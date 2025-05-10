package com.multirestaurantplatform.restaurant.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Set; // For future use if we include admin usernames or IDs

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantResponseDto {
    private Long id;
    private String name;
    private String description;
    private String address;
    private String phoneNumber;
    private String email;
    private boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
    // We can add a Set<String> restaurantAdminUsernames later if needed
    // For now, keeping it simple. The controller will map from the Restaurant entity.
}
