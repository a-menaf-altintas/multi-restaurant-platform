// File: backend/menu/src/main/java/com/multirestaurantplatform/menu/dto/MenuResponseDto.java
        package com.multirestaurantplatform.menu.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MenuResponseDto {

    private Long id;
    private String name;
    private String description;
    private boolean isActive;
    private Long restaurantId; // To show which restaurant this menu belongs to
    private Instant createdAt;
    private Instant updatedAt;
}
