package com.multirestaurantplatform.security.dto;

import com.multirestaurantplatform.security.model.Role; // Assuming Role is in this package
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDto {
    private Long id;
    private String username;
    private String email;
    private Set<Role> roles;

    // We can add a static factory method or use a mapping library like MapStruct later
    // for cleaner conversion from User entity to UserResponseDto if needed.
    // For now, manual mapping in the controller/service is fine for simplicity.
}