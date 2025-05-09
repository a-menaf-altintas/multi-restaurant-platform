package com.multirestaurantplatform.restaurant.model;

import com.multirestaurantplatform.common.model.BaseEntity;
import com.multirestaurantplatform.security.model.User; // Assuming User entity is in this package
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "restaurants")
public class Restaurant extends BaseEntity {

    @NotBlank(message = "Restaurant name cannot be blank")
    @Size(min = 2, max = 100, message = "Restaurant name must be between 2 and 100 characters")
    @Column(nullable = false, length = 100)
    private String name;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    @Column(length = 1000)
    private String description;

    @NotBlank(message = "Address cannot be blank")
    @Size(max = 255, message = "Address cannot exceed 255 characters")
    @Column(nullable = false)
    private String address; // Simple address for now, can be expanded to an Embeddable later

    @NotBlank(message = "Phone number cannot be blank")
    @Size(max = 20, message = "Phone number cannot exceed 20 characters")
    @Column(nullable = false, length = 20)
    private String phoneNumber;

    @Email(message = "Restaurant email should be valid")
    @Size(max = 100, message = "Restaurant email cannot exceed 100 characters")
    @Column(length = 100)
    private String email;

    @Column(nullable = false)
    private boolean isActive = true; // Default to active

    // Relationship to User entity for restaurant administrators
    // A restaurant can have multiple administrators (Users with RESTAURANT_ADMIN role)
    // A user (with RESTAURANT_ADMIN role) might administer multiple restaurants
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "restaurant_admins",
               joinColumns = @JoinColumn(name = "restaurant_id"),
               inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> restaurantAdmins = new HashSet<>();

    // toString, equals, and hashCode are inherited from BaseEntity (or can be customized if needed)
}