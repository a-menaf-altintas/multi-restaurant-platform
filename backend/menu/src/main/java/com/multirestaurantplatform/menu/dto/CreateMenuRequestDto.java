// File: backend/menu/src/main/java/com/multirestaurantplatform/menu/dto/CreateMenuRequestDto.java
package com.multirestaurantplatform.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateMenuRequestDto {

    @NotBlank(message = "Menu name cannot be blank")
    @Size(min = 2, max = 100, message = "Menu name must be between 2 and 100 characters")
    private String name;

    @Size(max = 500, message = "Description cannot exceed 500 characters") // Optional field
    private String description;

    @NotNull(message = "Restaurant ID cannot be null")
    private Long restaurantId;
}