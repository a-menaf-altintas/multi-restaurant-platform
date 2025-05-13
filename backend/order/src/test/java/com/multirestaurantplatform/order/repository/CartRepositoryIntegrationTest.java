package com.multirestaurantplatform.order.repository;

import com.multirestaurantplatform.order.model.cart.CartEntity;
import com.multirestaurantplatform.order.model.cart.CartItemEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class CartRepositoryIntegrationTest {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Test
    void findByUserId_ShouldReturnCart_WhenCartExists() {
        // Arrange
        String userId = "test-user";
        CartEntity cart = new CartEntity();
        cart.setUserId(userId);
        cart.setRestaurantId(1L);
        cart.setRestaurantName("Test Restaurant");
        cart.setCartTotalPrice(BigDecimal.ZERO);
        cart = cartRepository.save(cart);

        // Act
        Optional<CartEntity> result = cartRepository.findByUserId(userId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(userId, result.get().getUserId());
        assertEquals(1L, result.get().getRestaurantId());
    }

    @Test
    void existsByUserId_ShouldReturnTrue_WhenCartExists() {
        // Arrange
        String userId = "test-user";
        CartEntity cart = new CartEntity();
        cart.setUserId(userId);
        cart.setRestaurantId(1L);
        cart.setRestaurantName("Test Restaurant");
        cart.setCartTotalPrice(BigDecimal.ZERO);
        cartRepository.save(cart);

        // Act
        boolean exists = cartRepository.existsByUserId(userId);

        // Assert
        assertTrue(exists);
    }

    @Test
    void deleteByUserId_ShouldRemoveCart() {
        // Arrange
        String userId = "test-user";
        CartEntity cart = new CartEntity();
        cart.setUserId(userId);
        cart.setRestaurantId(1L);
        cart.setRestaurantName("Test Restaurant");
        cart.setCartTotalPrice(BigDecimal.ZERO);
        cartRepository.save(cart);

        // Act
        cartRepository.deleteByUserId(userId);

        // Assert
        assertFalse(cartRepository.existsByUserId(userId));
    }

    @Test
    void findByCartAndMenuItemId_ShouldReturnCartItem_WhenItemExists() {
        // Arrange
        String userId = "test-user";
        CartEntity cart = new CartEntity();
        cart.setUserId(userId);
        cart.setRestaurantId(1L);
        cart.setRestaurantName("Test Restaurant");
        cart.setCartTotalPrice(BigDecimal.ZERO);
        cart = cartRepository.save(cart);

        CartItemEntity cartItem = new CartItemEntity(101L, "Test Item", 2, new BigDecimal("12.99"));
        cartItem.setCart(cart);
        cartItemRepository.save(cartItem);

        // Act
        Optional<CartItemEntity> result = cartItemRepository.findByCartAndMenuItemId(cart, 101L);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(101L, result.get().getMenuItemId());
        assertEquals("Test Item", result.get().getMenuItemName());
        assertEquals(2, result.get().getQuantity());
    }

    @Test
    void deleteByCart_ShouldRemoveAllCartItems() {
        // Arrange
        String userId = "test-user";
        CartEntity cart = new CartEntity();
        cart.setUserId(userId);
        cart.setRestaurantId(1L);
        cart.setRestaurantName("Test Restaurant");
        cart.setCartTotalPrice(BigDecimal.ZERO);
        cart = cartRepository.save(cart);

        CartItemEntity cartItem1 = new CartItemEntity(101L, "Test Item 1", 2, new BigDecimal("12.99"));
        cartItem1.setCart(cart);
        cartItemRepository.save(cartItem1);

        CartItemEntity cartItem2 = new CartItemEntity(102L, "Test Item 2", 1, new BigDecimal("5.99"));
        cartItem2.setCart(cart);
        cartItemRepository.save(cartItem2);

        // Act
        cartItemRepository.deleteByCart(cart);

        // Assert
        assertEquals(0, cartItemRepository.findByCart(cart).size());
    }
}