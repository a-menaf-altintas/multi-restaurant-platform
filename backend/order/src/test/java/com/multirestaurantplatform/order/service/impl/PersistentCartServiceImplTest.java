package com.multirestaurantplatform.order.service.impl;

import com.multirestaurantplatform.order.dto.AddItemToCartRequest;
import com.multirestaurantplatform.order.dto.CartResponse;
import com.multirestaurantplatform.order.dto.UpdateCartItemRequest;
import com.multirestaurantplatform.order.exception.CartNotFoundException;
import com.multirestaurantplatform.order.exception.MenuItemNotFoundInCartException;
import com.multirestaurantplatform.order.model.cart.CartEntity;
import com.multirestaurantplatform.order.model.cart.CartItemEntity;
import com.multirestaurantplatform.order.repository.CartItemRepository;
import com.multirestaurantplatform.order.repository.CartRepository;
import com.multirestaurantplatform.order.service.client.MenuItemDetailsDto;
import com.multirestaurantplatform.order.service.client.MenuServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersistentCartServiceImplTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private MenuServiceClient menuServiceClient;

    @InjectMocks
    private PersistentCartServiceImpl cartService;

    private String userId;
    private CartEntity testCart;
    private CartItemEntity testCartItem;
    private MenuItemDetailsDto testMenuItemDetails;

    @BeforeEach
    void setUp() {
        userId = "user123";

        // Set up test cart
        testCart = new CartEntity();
        testCart.setId(1L);
        testCart.setUserId(userId);
        testCart.setRestaurantId(1L);
        testCart.setRestaurantName("Test Restaurant");
        testCart.setCartTotalPrice(new BigDecimal("25.98"));
        testCart.setItems(new ArrayList<>());

        // Set up test cart item
        testCartItem = new CartItemEntity(101L, "Test Item", 2, new BigDecimal("12.99"));
        testCartItem.setId(1L);
        testCartItem.setCart(testCart);
        testCart.getItems().add(testCartItem);

        // Set up test menu item details
        testMenuItemDetails = new MenuItemDetailsDto(
                101L,
                "Test Item",
                new BigDecimal("12.99"),
                1L,
                "Test Restaurant",
                true
        );
    }

    @Test
    void getCart_WhenCartExists_ShouldReturnCart() {
        // Arrange
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(testCart));

        // Act
        CartResponse result = cartService.getCart(userId);

        // Assert
        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals(1L, result.getRestaurantId());
        assertEquals("Test Restaurant", result.getRestaurantName());
        assertEquals(1, result.getItems().size());
        assertEquals(new BigDecimal("25.98"), result.getCartTotalPrice());

        verify(cartRepository).findByUserId(userId);
    }

    @Test
    void getCart_WhenCartDoesNotExist_ShouldReturnEmptyCart() {
        // Arrange
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());

        // Act
        CartResponse result = cartService.getCart(userId);

        // Assert
        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertNull(result.getRestaurantId());
        assertNull(result.getRestaurantName());
        assertTrue(result.getItems().isEmpty());
        assertEquals(BigDecimal.ZERO, result.getCartTotalPrice());

        verify(cartRepository).findByUserId(userId);
    }

    @Test
    void addItemToCart_WithNewCart_ShouldCreateCartAndAddItem() {
        // Arrange
        AddItemToCartRequest request = new AddItemToCartRequest(1L, 101L, 2);
        when(menuServiceClient.getMenuItemDetails(anyLong(), anyLong())).thenReturn(Optional.of(testMenuItemDetails));
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(cartRepository.save(any(CartEntity.class))).thenAnswer(invocation -> {
            CartEntity cart = invocation.getArgument(0);
            if (cart.getId() == null) {
                cart.setId(1L);
            }
            return cart;
        });
        when(cartItemRepository.save(any(CartItemEntity.class))).thenAnswer(invocation -> {
            CartItemEntity item = invocation.getArgument(0);
            if (item.getId() == null) {
                item.setId(1L);
            }
            return item;
        });

        // Act
        CartResponse result = cartService.addItemToCart(userId, request);

        // Assert
        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals(1L, result.getRestaurantId());
        assertEquals("Test Restaurant", result.getRestaurantName());
        assertEquals(1, result.getItems().size());
        assertEquals(101L, result.getItems().get(0).getMenuItemId());
        assertEquals(2, result.getItems().get(0).getQuantity());

        verify(menuServiceClient).getMenuItemDetails(101L, 1L);
        verify(cartRepository).findByUserId(userId);
        verify(cartRepository, times(2)).save(any(CartEntity.class));
        verify(cartItemRepository).save(any(CartItemEntity.class));
    }

    @Test
    void addItemToCart_WithExistingCart_ShouldAddItem() {
        // Arrange
        testCart.getItems().clear(); // Start with empty cart
        AddItemToCartRequest request = new AddItemToCartRequest(1L, 101L, 2);
        when(menuServiceClient.getMenuItemDetails(anyLong(), anyLong())).thenReturn(Optional.of(testMenuItemDetails));
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(testCart));
        when(cartItemRepository.findByCartAndMenuItemId(any(CartEntity.class), anyLong())).thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItemEntity.class))).thenAnswer(invocation -> {
            CartItemEntity item = invocation.getArgument(0);
            if (item.getId() == null) {
                item.setId(1L);
            }
            return item;
        });

        // Act
        CartResponse result = cartService.addItemToCart(userId, request);

        // Assert
        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals(1, result.getItems().size());
        assertEquals(101L, result.getItems().get(0).getMenuItemId());
        assertEquals(2, result.getItems().get(0).getQuantity());

        verify(menuServiceClient).getMenuItemDetails(101L, 1L);
        verify(cartRepository).findByUserId(userId);
        verify(cartRepository).save(any(CartEntity.class));
        verify(cartItemRepository).save(any(CartItemEntity.class));
    }

    @Test
    void updateCartItem_ShouldUpdateQuantity() {
        // Arrange
        UpdateCartItemRequest request = new UpdateCartItemRequest(5);
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(testCart));
        when(cartItemRepository.findByCartAndMenuItemId(testCart, 101L)).thenReturn(Optional.of(testCartItem));

        // Act
        CartResponse result = cartService.updateCartItem(userId, 101L, request);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        assertEquals(5, result.getItems().get(0).getQuantity());

        verify(cartRepository).findByUserId(userId);
        verify(cartItemRepository).findByCartAndMenuItemId(testCart, 101L);
        verify(cartItemRepository).save(testCartItem);
        verify(cartRepository).save(testCart);
    }

    @Test
    void updateCartItem_WhenCartNotFound_ShouldThrowException() {
        // Arrange
        UpdateCartItemRequest request = new UpdateCartItemRequest(5);
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(CartNotFoundException.class, () -> {
            cartService.updateCartItem(userId, 101L, request);
        });

        verify(cartRepository).findByUserId(userId);
        verifyNoInteractions(cartItemRepository);
    }

    @Test
    void updateCartItem_WhenItemNotFound_ShouldThrowException() {
        // Arrange
        UpdateCartItemRequest request = new UpdateCartItemRequest(5);
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(testCart));
        when(cartItemRepository.findByCartAndMenuItemId(testCart, 999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(MenuItemNotFoundInCartException.class, () -> {
            cartService.updateCartItem(userId, 999L, request);
        });

        verify(cartRepository).findByUserId(userId);
        verify(cartItemRepository).findByCartAndMenuItemId(testCart, 999L);
    }

    @Test
    void removeCartItem_ShouldRemoveItem() {
        // Arrange
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(testCart));
        when(cartItemRepository.findByCartAndMenuItemId(testCart, 101L)).thenReturn(Optional.of(testCartItem));

        // Act
        CartResponse result = cartService.removeCartItem(userId, 101L);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getItems().size()); // Item should be removed

        verify(cartRepository).findByUserId(userId);
        verify(cartItemRepository).findByCartAndMenuItemId(testCart, 101L);
        verify(cartItemRepository).delete(testCartItem);
        verify(cartRepository).save(testCart);
    }

    @Test
    void clearCart_ShouldClearAllItems() {
        // Arrange
        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(testCart));

        // Act
        cartService.clearCart(userId);

        // Assert
        verify(cartRepository).findByUserId(userId);
        verify(cartItemRepository).deleteByCart(testCart);
        verify(cartRepository).save(testCart);
        
        // The cart should be reset but not deleted
        assertNull(testCart.getRestaurantId());
        assertNull(testCart.getRestaurantName());
        assertTrue(testCart.getItems().isEmpty());
        assertEquals(BigDecimal.ZERO, testCart.getCartTotalPrice());
    }
}