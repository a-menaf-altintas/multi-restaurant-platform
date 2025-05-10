package com.multirestaurantplatform.restaurant.repository;

import com.multirestaurantplatform.restaurant.model.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    // Example: Find a restaurant by its exact name (case-sensitive)
    Optional<Restaurant> findByName(String name);

    // Example: Find a restaurant by its email (case-sensitive)
    Optional<Restaurant> findByEmail(String email);

    // You can add more custom query methods here as needed, e.g.:
    // List<Restaurant> findByIsActiveTrue();
    // Page<Restaurant> findByNameContainingIgnoreCase(String name, Pageable pageable);
}