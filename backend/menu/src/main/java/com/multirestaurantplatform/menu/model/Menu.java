package com.multirestaurantplatform.menu.model;

import com.multirestaurantplatform.common.model.BaseEntity;
import com.multirestaurantplatform.restaurant.model.Restaurant; // Import Restaurant
import jakarta.persistence.*;
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
@Entity
@Table(name = "menus", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"restaurant_id", "name"}, name = "uq_menus_restaurant_id_name")
})
public class Menu extends BaseEntity {

    @NotBlank(message = "Menu name cannot be blank")
    @Size(min = 2, max = 100, message = "Menu name must be between 2 and 100 characters")
    @Column(nullable = false, length = 100)
    private String name;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean isActive = true;

    @NotNull(message = "Menu must be associated with a restaurant")
    @ManyToOne(fetch = FetchType.LAZY) // Many menus can belong to one restaurant
    @JoinColumn(name = "restaurant_id", nullable = false) // Foreign key column in the 'menus' table
    private Restaurant restaurant;

    // toString, equals, and hashCode are inherited from BaseEntity (or can be customized if needed)
    // We might add @OneToMany for MenuSection later
}
