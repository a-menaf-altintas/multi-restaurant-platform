package com.multirestaurantplatform.security.model;

import com.multirestaurantplatform.common.model.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor; // Example constructor if needed
import lombok.AllArgsConstructor; // Example constructor if needed

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor // Generates a no-args constructor (required by JPA)
@AllArgsConstructor // Optional: Generates an all-args constructor
@Entity
@Table(name = "users", // Explicitly naming the table "users"
       uniqueConstraints = { // Adding unique constraints at the table level
           @UniqueConstraint(columnNames = "username"),
           @UniqueConstraint(columnNames = "email")
       })
public class User extends BaseEntity {

    @NotBlank // From jakarta.validation.constraints - ensures not null and not just whitespace
    @Size(min = 3, max = 50)
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @NotBlank
    @Size(min = 8, max = 100) // Store hashed passwords, so length should be sufficient
    @Column(nullable = false, length = 100)
    private String password; // Store hashed passwords ONLY

    @NotBlank
    @Email // Validates if the string is a well-formed email address
    @Size(max = 100)
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @NotNull // A user must have at least one role
    @Enumerated(EnumType.STRING) // Store the enum name (e.g., "ADMIN") as a string in the DB
    @ElementCollection(fetch = FetchType.EAGER) // Store roles in a separate table (user_roles)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id")) // Customize the join table
    @Column(name = "role", nullable = false) // Column name in the join table
    private Set<Role> roles;

    // Optional: Add other fields like firstName, lastName, phoneNumber, isActive, etc. later
    // private String firstName;
    // private String lastName;
    // private String phoneNumber;
    // private boolean isActive = true; // Default to active

    // Inherits id, createdAt, updatedAt from BaseEntity
    // Inherits equals() and hashCode() from BaseEntity (based on ID)
}