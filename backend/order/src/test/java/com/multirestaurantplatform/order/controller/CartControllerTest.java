package com.multirestaurantplatform.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multirestaurantplatform.order.dto.AddItemToCartRequest;
import com.multirestaurantplatform.order.dto.CartItemResponse;
import com.multirestaurantplatform.order.dto.CartResponse;
import com.multirestaurantplatform.order.dto.UpdateCartItemRequest;
import com.multirestaurantplatform.order.exception.CartNotFoundException;
import com.multirestaurantplatform.order.exception.MenuItemNotFoundInCartException;
import com.multirestaurantplatform.order.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CartController.
 * Using @ExtendWith(MockitoExtension.class) instead of loading the Spring context
 * to keep tests lightweight and focused.
 */
@ExtendWith(MockitoExtension.class)
public class CartControllerTest {

    @Mock
    private CartService cartService;

    @InjectMocks
    private CartController cartController;

    private CartResponse mockCartResponse;
    private String userId;

    @BeforeEach
    void setUp() {
        userId = "user123";

        // Create a mock cart response with sample items
        CartItemResponse item1 = new CartItemResponse(
                101L, "Classic Burger", 2,
                new BigDecimal("12.99"), new BigDecimal("25.98"));

        CartItemResponse item2 = new CartItemResponse(
                102L, "Cheese Fries", 1,
                new BigDecimal("5.50"), new BigDecimal("5.50"));

        mockCartResponse = new CartResponse(
                userId, 1L, "Burger Queen",
                List.of(item1, item2), new BigDecimal("31.48"));
    }

    @Test
    void getCart_ShouldReturnCart_WhenCartExists() {
        // Arrange
        when(cartService.getCart(userId)).thenReturn(mockCartResponse);

        // Act
        ResponseEntity<CartResponse> response = cartController.getCart(userId);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(userId, response.getBody().getUserId());
        assertEquals(1L, response.getBody().getRestaurantId());
        assertEquals("Burger Queen", response.getBody().getRestaurantName());
        assertEquals(2, response.getBody().getItems().size());
        assertEquals(new BigDecimal("31.48"), response.getBody().getCartTotalPrice());
    }

    @Test
    void addItemToCart_ShouldReturnUpdatedCart_WhenItemAdded() {
        // Arrange
        AddItemToCartRequest request = new AddItemToCartRequest(1L, 101L, 2);
        when(cartService.addItemToCart(eq(userId), any(AddItemToCartRequest.class)))
                .thenReturn(mockCartResponse);

        // Act
        ResponseEntity<CartResponse> response = cartController.addItemToCart(userId, request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(userId, response.getBody().getUserId());
    }

    @Test
    void updateCartItem_ShouldReturnUpdatedCart_WhenQuantityChanged() {
        // Arrange
        Long menuItemId = 101L;
        UpdateCartItemRequest request = new UpdateCartItemRequest(3);
        when(cartService.updateCartItem(eq(userId), eq(menuItemId), any(UpdateCartItemRequest.class)))
                .thenReturn(mockCartResponse);

        // Act
        ResponseEntity<CartResponse> response = cartController.updateCartItem(userId, menuItemId, request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(userId, response.getBody().getUserId());
    }

    @Test
    void removeCartItem_ShouldReturnUpdatedCart_WhenItemRemoved() {
        // Arrange
        Long menuItemId = 101L;
        when(cartService.removeCartItem(userId, menuItemId))
                .thenReturn(mockCartResponse);

        // Act
        ResponseEntity<CartResponse> response = cartController.removeCartItem(userId, menuItemId);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(userId, response.getBody().getUserId());
    }

    @Test
    void clearCart_ShouldReturnNoContent_WhenCartCleared() {
        // Arrange
        doNothing().when(cartService).clearCart(userId);

        // Act
        ResponseEntity<Void> response = cartController.clearCart(userId);

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void updateCartItem_ShouldReturnNotFound_WhenCartNotFound() {
        // Arrange
        Long menuItemId = 101L;
        UpdateCartItemRequest request = new UpdateCartItemRequest(3);

        when(cartService.updateCartItem(eq(userId), eq(menuItemId), any(UpdateCartItemRequest.class)))
                .thenThrow(new CartNotFoundException("Cart not found for user " + userId));

        // Act & Assert - Check exception handling is delegated to GlobalExceptionHandler
        try {
            cartController.updateCartItem(userId, menuItemId, request);
        } catch (CartNotFoundException ex) {
            assertEquals("Cart not found for user " + userId, ex.getMessage());
        }
    }

    @Test
    void removeCartItem_ShouldReturnNotFound_WhenMenuItemNotInCart() {
        // Arrange
        Long menuItemId = 999L; // Non-existent item

        when(cartService.removeCartItem(userId, menuItemId))
                .thenThrow(new MenuItemNotFoundInCartException("Menu item " + menuItemId + " not found in cart."));

        // Act & Assert - Check exception handling is delegated to GlobalExceptionHandler
        try {
            cartController.removeCartItem(userId, menuItemId);
        } catch (MenuItemNotFoundInCartException ex) {
            assertEquals("Menu item " + menuItemId + " not found in cart.", ex.getMessage());
        }
    }
}