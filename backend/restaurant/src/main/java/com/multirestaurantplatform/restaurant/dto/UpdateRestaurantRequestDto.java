// File: backend/restaurant/src/main/java/com/multirestaurantplatform/restaurant/dto/UpdateRestaurantRequestDto.java
package com.multirestaurantplatform.restaurant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Set; // Import Set

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRestaurantRequestDto {

    @Size(min = 2, max = 100, message = "Restaurant name must be between 2 and 100 characters")
    private String name; // Can be null

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description; // Can be null

    @Size(max = 255, message = "Address cannot exceed 255 characters")
    private String address; // Can be null

    @Size(max = 20, message = "Phone number cannot exceed 20 characters")
    private String phoneNumber; // Can be null

    @Email(message = "Restaurant email should be valid")
    @Size(max = 100, message = "Restaurant email cannot exceed 100 characters")
    private String email; // Can be null

    private Boolean isActive; // Can be null (use Boolean wrapper type)

    // New field to manage restaurant administrators
    // This field is optional. If provided, it will replace the existing set of admins for the restaurant.
    // If null, the admins will not be changed. If an empty set, all admins will be removed.
    private Set<Long> adminUserIds; // Can be null
}
