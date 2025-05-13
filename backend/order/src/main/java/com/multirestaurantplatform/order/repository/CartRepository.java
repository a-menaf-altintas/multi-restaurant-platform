package com.multirestaurantplatform.order.repository;

import com.multirestaurantplatform.order.model.cart.CartEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for accessing and manipulating CartEntity objects in the database.
 */
@Repository
public interface CartRepository extends JpaRepository<CartEntity, Long> {

    /**
     * Find a cart by the user ID.
     *
     * @param userId The ID of the user whose cart to find
     * @return An Optional containing the cart if found, empty otherwise
     */
    Optional<CartEntity> findByUserId(String userId);

    /**
     * Check if a cart exists for the given user ID.
     *
     * @param userId The ID of the user to check
     * @return true if a cart exists for the user, false otherwise
     */
    boolean existsByUserId(String userId);

    /**
     * Delete a cart by the user ID.
     *
     * @param userId The ID of the user whose cart to delete
     */
    void deleteByUserId(String userId);
}