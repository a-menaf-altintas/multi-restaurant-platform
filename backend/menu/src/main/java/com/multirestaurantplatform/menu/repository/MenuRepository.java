package com.multirestaurantplatform.menu.repository;

import com.multirestaurantplatform.menu.model.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MenuRepository extends JpaRepository<Menu, Long> {

    // Find all menus for a specific restaurant
    List<Menu> findByRestaurantId(Long restaurantId);

    // Find all active menus for a specific restaurant
    List<Menu> findByRestaurantIdAndIsActiveTrue(Long restaurantId);

    // Find a menu by its name and restaurant ID (to check for duplicates within a restaurant)
    Optional<Menu> findByRestaurantIdAndNameIgnoreCase(Long restaurantId, String name);

    // You can add more custom query methods here as needed.
}
