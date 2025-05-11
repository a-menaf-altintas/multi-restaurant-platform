// File: backend/menu/src/main/java/com/multirestaurantplatform/menu/dto/UpdateMenuRequestDto.java
package com.multirestaurantplatform.menu.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMenuRequestDto {

    @Size(min = 2, max = 100, message = "Menu name must be between 2 and 100 characters")
    private String name; // Optional: if null, not updated

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description; // Optional: if null, not updated

    private Boolean isActive; // Optional: if null, not updated
}