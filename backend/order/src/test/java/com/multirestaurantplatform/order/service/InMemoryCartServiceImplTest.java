package com.multirestaurantplatform.order.service;

import com.multirestaurantplatform.order.dto.AddItemToCartRequest;
import com.multirestaurantplatform.order.dto.CartResponse;
import com.multirestaurantplatform.order.dto.UpdateCartItemRequest;
import com.multirestaurantplatform.order.exception.CartNotFoundException;
import com.multirestaurantplatform.order.exception.CartUpdateException;
import com.multirestaurantplatform.order.exception.MenuItemNotFoundInCartException;
import com.multirestaurantplatform.order.service.client.MenuItemDetailsDto;
import com.multirestaurantplatform.order.service.client.MenuServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InMemoryCartServiceImplTest {

    @Mock
    private MenuServiceClient menuServiceClient;

    private InMemoryCartServiceImpl cartService;
    private final String userId = "user123";
    private final Long restaurantId = 1L;
    private final Long menuItemId = 101L;
    private final String restaurantName = "Test Restaurant";
    private final String menuItemName = "Test Item";
    private final BigDecimal price = new BigDecimal("10.99");

    @BeforeEach
    void setUp() {
        cartService = new InMemoryCartServiceImpl(menuServiceClient);
    }

    @Test
    void testAddItemToCart_Success() {
        // Arrange
        AddItemToCartRequest request = new AddItemToCartRequest(restaurantId, menuItemId, 2);
        MenuItemDetailsDto itemDetails = new MenuItemDetailsDto(
                menuItemId, menuItemName, price, restaurantId, restaurantName, true);
        
        when(menuServiceClient.getMenuItemDetails(anyLong(), anyLong()))
                .thenReturn(Optional.of(itemDetails));

        // Act
        CartResponse response = cartService.addItemToCart(userId, request);

        // Assert
        assertNotNull(response);
        assertEquals(userId, response.getUserId());
        assertEquals(restaurantId, response.getRestaurantId());
        assertEquals(restaurantName, response.getRestaurantName());
        assertEquals(1, response.getItems().size());
        assertEquals(menuItemId, response.getItems().get(0).getMenuItemId());
        assertEquals(menuItemName, response.getItems().get(0).getMenuItemName());
        assertEquals(2, response.getItems().get(0).getQuantity());
        assertEquals(price, response.getItems().get(0).getUnitPrice());
        assertEquals(price.multiply(new BigDecimal("2")), response.getItems().get(0).getTotalPrice());
        assertEquals(price.multiply(new BigDecimal("2")), response.getCartTotalPrice());
        
        verify(menuServiceClient, times(1)).getMenuItemDetails(menuItemId, restaurantId);
    }

    @Test
    void testAddItemToCart_ItemNotFound() {
        // Arrange
        AddItemToCartRequest request = new AddItemToCartRequest(restaurantId, menuItemId, 2);
        
        when(menuServiceClient.getMenuItemDetails(anyLong(), anyLong()))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(CartUpdateException.class, () -> cartService.addItemToCart(userId, request));
        verify(menuServiceClient, times(1)).getMenuItemDetails(menuItemId, restaurantId);
    }

    @Test
    void testAddItemToCart_ItemUnavailable() {
        // Arrange
        AddItemToCartRequest request = new AddItemToCartRequest(restaurantId, menuItemId, 2);
        MenuItemDetailsDto itemDetails = new MenuItemDetailsDto(
                menuItemId, menuItemName, price, restaurantId, restaurantName, false); // not available
        
        when(menuServiceClient.getMenuItemDetails(anyLong(), anyLong()))
                .thenReturn(Optional.of(itemDetails));

        // Act & Assert
        assertThrows(CartUpdateException.class, () -> cartService.addItemToCart(userId, request));
        verify(menuServiceClient, times(1)).getMenuItemDetails(menuItemId, restaurantId);
    }

    @Test
    void testAddItemToCart_DifferentRestaurant() {
        // Arrange
        // First add item from restaurant 1
        AddItemToCartRequest request1 = new AddItemToCartRequest(restaurantId, menuItemId, 1);
        MenuItemDetailsDto itemDetails1 = new MenuItemDetailsDto(
                menuItemId, menuItemName, price, restaurantId, restaurantName, true);
        
        when(menuServiceClient.getMenuItemDetails(eq(menuItemId), eq(restaurantId)))
                .thenReturn(Optional.of(itemDetails1));
        
        cartService.addItemToCart(userId, request1);
        
        // Then try to add item from restaurant 2
        Long restaurant2Id = 2L;
        String restaurant2Name = "Second Restaurant";
        Long menuItem2Id = 201L;
        AddItemToCartRequest request2 = new AddItemToCartRequest(restaurant2Id, menuItem2Id, 1);
        MenuItemDetailsDto itemDetails2 = new MenuItemDetailsDto(
                menuItem2Id, "Second Item", price, restaurant2Id, restaurant2Name, true);
        
        when(menuServiceClient.getMenuItemDetails(eq(menuItem2Id), eq(restaurant2Id)))
                .thenReturn(Optional.of(itemDetails2));
        
        // Act
        CartResponse response = cartService.addItemToCart(userId, request2);

        // Assert - Cart should be cleared and only contain the new item
        assertNotNull(response);
        assertEquals(restaurant2Id, response.getRestaurantId());
        assertEquals(restaurant2Name, response.getRestaurantName());
        assertEquals(1, response.getItems().size());
        assertEquals(menuItem2Id, response.getItems().get(0).getMenuItemId());
    }

    @Test
    void testAddItemToCart_ExistingItem_IncreasesQuantity() {
        // Arrange
        AddItemToCartRequest request = new AddItemToCartRequest(restaurantId, menuItemId, 2);
        MenuItemDetailsDto itemDetails = new MenuItemDetailsDto(
                menuItemId, menuItemName, price, restaurantId, restaurantName, true);
        
        when(menuServiceClient.getMenuItemDetails(anyLong(), anyLong()))
                .thenReturn(Optional.of(itemDetails));
        
        // Add item first time
        cartService.addItemToCart(userId, request);
        
        // Act - Add same item second time
        CartResponse response = cartService.addItemToCart(userId, request);

        // Assert - Quantity should be increased
        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertEquals(menuItemId, response.getItems().get(0).getMenuItemId());
        assertEquals(4, response.getItems().get(0).getQuantity()); // 2 + 2 = 4
        assertEquals(price.multiply(new BigDecimal("4")), response.getCartTotalPrice());
    }

    @Test
    void testGetCart_EmptyCart() {
        // Act
        CartResponse response = cartService.getCart("nonexistent-user");

        // Assert
        assertNotNull(response);
        assertEquals("nonexistent-user", response.getUserId());
        assertNull(response.getRestaurantId());
        assertNull(response.getRestaurantName());
        assertTrue(response.getItems().isEmpty());
        assertEquals(BigDecimal.ZERO, response.getCartTotalPrice());
    }

    @Test
    void testUpdateCartItem_Success() {
        // Arrange - Add item to cart first
        AddItemToCartRequest addRequest = new AddItemToCartRequest(restaurantId, menuItemId, 2);
        MenuItemDetailsDto itemDetails = new MenuItemDetailsDto(
                menuItemId, menuItemName, price, restaurantId, restaurantName, true);
        
        when(menuServiceClient.getMenuItemDetails(anyLong(), anyLong()))
                .thenReturn(Optional.of(itemDetails));
        
        cartService.addItemToCart(userId, addRequest);
        
        // Act - Update the item quantity
        UpdateCartItemRequest updateRequest = new UpdateCartItemRequest(5);
        CartResponse response = cartService.updateCartItem(userId, menuItemId, updateRequest);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertEquals(5, response.getItems().get(0).getQuantity());
        assertEquals(price.multiply(new BigDecimal("5")), response.getCartTotalPrice());
    }

    @Test
    void testUpdateCartItem_CartNotFound() {
        // Act & Assert
        UpdateCartItemRequest updateRequest = new UpdateCartItemRequest(5);
        assertThrows(CartNotFoundException.class, 
                () -> cartService.updateCartItem("nonexistent-user", menuItemId, updateRequest));
    }

    @Test
    void testUpdateCartItem_MenuItemNotFound() {
        // Arrange - Add item to cart first
        AddItemToCartRequest addRequest = new AddItemToCartRequest(restaurantId, menuItemId, 2);
        MenuItemDetailsDto itemDetails = new MenuItemDetailsDto(
                menuItemId, menuItemName, price, restaurantId, restaurantName, true);
        
        when(menuServiceClient.getMenuItemDetails(anyLong(), anyLong()))
                .thenReturn(Optional.of(itemDetails));
        
        cartService.addItemToCart(userId, addRequest);
        
        // Act & Assert - Try to update nonexistent item
        UpdateCartItemRequest updateRequest = new UpdateCartItemRequest(5);
        assertThrows(MenuItemNotFoundInCartException.class, 
                () -> cartService.updateCartItem(userId, 999L, updateRequest));
    }

    @Test
    void testRemoveCartItem_Success() {
        // Arrange - Add item to cart first
        AddItemToCartRequest addRequest = new AddItemToCartRequest(restaurantId, menuItemId, 2);
        MenuItemDetailsDto itemDetails = new MenuItemDetailsDto(
                menuItemId, menuItemName, price, restaurantId, restaurantName, true);
        
        when(menuServiceClient.getMenuItemDetails(anyLong(), anyLong()))
                .thenReturn(Optional.of(itemDetails));
        
        cartService.addItemToCart(userId, addRequest);
        
        // Act - Remove the item
        CartResponse response = cartService.removeCartItem(userId, menuItemId);

        // Assert
        assertNotNull(response);
        assertTrue(response.getItems().isEmpty());
        assertEquals(BigDecimal.ZERO, response.getCartTotalPrice());
        assertNull(response.getRestaurantId());
        assertNull(response.getRestaurantName());
    }

    @Test
    void testRemoveCartItem_CartNotFound() {
        // Act & Assert
        assertThrows(CartNotFoundException.class, 
                () -> cartService.removeCartItem("nonexistent-user", menuItemId));
    }

    @Test
    void testRemoveCartItem_MenuItemNotFound() {
        // Arrange - Add item to cart first
        AddItemToCartRequest addRequest = new AddItemToCartRequest(restaurantId, menuItemId, 2);
        MenuItemDetailsDto itemDetails = new MenuItemDetailsDto(
                menuItemId, menuItemName, price, restaurantId, restaurantName, true);
        
        when(menuServiceClient.getMenuItemDetails(anyLong(), anyLong()))
                .thenReturn(Optional.of(itemDetails));
        
        cartService.addItemToCart(userId, addRequest);
        
        // Act & Assert - Try to remove nonexistent item
        assertThrows(MenuItemNotFoundInCartException.class, 
                () -> cartService.removeCartItem(userId, 999L));
    }

    @Test
    void testClearCart_Success() {
        // Arrange - Add item to cart first
        AddItemToCartRequest addRequest = new AddItemToCartRequest(restaurantId, menuItemId, 2);
        MenuItemDetailsDto itemDetails = new MenuItemDetailsDto(
                menuItemId, menuItemName, price, restaurantId, restaurantName, true);
        
        when(menuServiceClient.getMenuItemDetails(anyLong(), anyLong()))
                .thenReturn(Optional.of(itemDetails));
        
        cartService.addItemToCart(userId, addRequest);
        
        // Act - Clear the cart
        cartService.clearCart(userId);
        
        // Assert - Get cart should return empty cart
        CartResponse response = cartService.getCart(userId);
        assertNotNull(response);
        assertEquals(userId, response.getUserId());
        assertNull(response.getRestaurantId());
        assertNull(response.getRestaurantName());
        assertTrue(response.getItems().isEmpty());
        assertEquals(BigDecimal.ZERO, response.getCartTotalPrice());
    }

    @Test
    void testClearCart_NonExistentCart() {
        // Act - This should not throw an exception
        cartService.clearCart("nonexistent-user");
        
        // Assert - Verify the cart is still empty/non-existent
        CartResponse response = cartService.getCart("nonexistent-user");
        assertNotNull(response);
        assertTrue(response.getItems().isEmpty());
    }
}