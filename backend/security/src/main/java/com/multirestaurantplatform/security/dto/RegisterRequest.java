package com.multirestaurantplatform.security.dto;

import com.multirestaurantplatform.security.model.Role; // Assuming Role enum is in model package
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data; // Lombok annotation for boilerplate code (getters, setters, equals, hashCode, toString)

import java.util.Set;

@Data // Bundles @Getter, @Setter, @ToString, @EqualsAndHashCode, @RequiredArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Username cannot be blank")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @NotBlank(message = "Password cannot be blank")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters") // Validate length before hashing
    private String password;

    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Email should be valid")
    @Size(max = 100, message = "Email cannot exceed 100 characters")
    private String email;

    @NotEmpty(message = "User must have at least one role")
    private Set<Role> roles; // Specify the roles during registration (e.g., CUSTOMER)
                            // In a real app, you might default this or derive it differently.
}