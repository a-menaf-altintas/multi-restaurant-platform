package com.multirestaurantplatform.restaurant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Optional;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRestaurantRequestDto {

    // Fields are optional for update
    @Size(min = 2, max = 100, message = "Restaurant name must be between 2 and 100 characters")
    private Optional<String> name = Optional.empty();

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private Optional<String> description = Optional.empty();

    @Size(max = 255, message = "Address cannot exceed 255 characters")
    private Optional<String> address = Optional.empty();

    @Size(max = 20, message = "Phone number cannot exceed 20 characters")
    private Optional<String> phoneNumber = Optional.empty();

    @Email(message = "Restaurant email should be valid")
    @Size(max = 100, message = "Restaurant email cannot exceed 100 characters")
    private Optional<String> email = Optional.empty();

    private Optional<Boolean> isActive = Optional.empty();

    // We can add fields for managing restaurant admins (e.g., Set<Long> addAdminUserIds, Set<Long> removeAdminUserIds) later.
}