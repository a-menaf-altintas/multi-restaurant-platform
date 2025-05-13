package com.multirestaurantplatform.order.repository;

import com.multirestaurantplatform.order.model.cart.CartEntity;
import com.multirestaurantplatform.order.model.cart.CartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for accessing and manipulating CartItemEntity objects in the database.
 */
@Repository
public interface CartItemRepository extends JpaRepository<CartItemEntity, Long> {

    /**
     * Find all cart items belonging to a specific cart.
     *
     * @param cart The cart whose items to find
     * @return A list of cart items belonging to the cart
     */
    List<CartItemEntity> findByCart(CartEntity cart);

    /**
     * Find a specific cart item by cart and menu item ID.
     *
     * @param cart The cart to search in
     * @param menuItemId The ID of the menu item to find
     * @return An Optional containing the cart item if found, empty otherwise
     */
    Optional<CartItemEntity> findByCartAndMenuItemId(CartEntity cart, Long menuItemId);

    /**
     * Check if a cart item exists in a cart for a specific menu item.
     *
     * @param cart The cart to check
     * @param menuItemId The ID of the menu item to check for
     * @return true if the cart item exists, false otherwise
     */
    boolean existsByCartAndMenuItemId(CartEntity cart, Long menuItemId);

    /**
     * Delete all cart items belonging to a specific cart.
     *
     * @param cart The cart whose items to delete
     */
    void deleteByCart(CartEntity cart);
}