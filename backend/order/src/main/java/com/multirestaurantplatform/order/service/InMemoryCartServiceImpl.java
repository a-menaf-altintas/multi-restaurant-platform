package com.multirestaurantplatform.order.service;

import com.multirestaurantplatform.order.dto.*;
import com.multirestaurantplatform.order.exception.CartNotFoundException;
import com.multirestaurantplatform.order.exception.CartUpdateException;
import com.multirestaurantplatform.order.exception.MenuItemNotFoundInCartException;
import com.multirestaurantplatform.order.model.cart.Cart;
import com.multirestaurantplatform.order.model.cart.CartItem;
import com.multirestaurantplatform.order.service.client.MenuItemDetailsDto;
import com.multirestaurantplatform.order.service.client.MenuServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class InMemoryCartServiceImpl implements CartService {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryCartServiceImpl.class);
    private final Map<String, Cart> userCarts = new ConcurrentHashMap<>();
    private final MenuServiceClient menuServiceClient; // To fetch item details

    @Autowired
    public InMemoryCartServiceImpl(MenuServiceClient menuServiceClient) {
        this.menuServiceClient = menuServiceClient;
        // Initialize with a Stub implementation for now if a real one isn't available
        // This will be replaced by a proper client (e.g. Feign client or direct service call)
        logger.info("InMemoryCartServiceImpl initialized. MenuServiceClient type: {}",
            menuServiceClient.getClass().getSimpleName());
    }

    @Override
    public CartResponse addItemToCart(String userId, AddItemToCartRequest addItemRequest) {
        MenuItemDetailsDto itemDetails = menuServiceClient
                .getMenuItemDetails(addItemRequest.getMenuItemId(), addItemRequest.getRestaurantId())
                .orElseThrow(() -> new CartUpdateException("Menu item " + addItemRequest.getMenuItemId() + " not found or unavailable."));

        if (!itemDetails.isAvailable()) {
            throw new CartUpdateException("Menu item " + itemDetails.getName() + " is currently unavailable.");
        }
        
        if (!itemDetails.getRestaurantId().equals(addItemRequest.getRestaurantId())) {
            // This check might be redundant if getMenuItemDetails already uses restaurantId for lookup scope
            throw new CartUpdateException("Menu item " + itemDetails.getName() + " does not belong to restaurant " + addItemRequest.getRestaurantId());
        }

        Cart cart = userCarts.computeIfAbsent(userId, k -> new Cart(userId, itemDetails.getRestaurantId(), itemDetails.getRestaurantName()));

        // Check if item is from a different restaurant than the current cart
        if (!cart.getRestaurantId().equals(itemDetails.getRestaurantId())) {
            // Policy: Clear cart if adding from a new restaurant, or throw error.
            // For now, let's clear and start a new cart.
            logger.info("User {} adding item from new restaurant {}. Clearing old cart from restaurant {}.",
                    userId, itemDetails.getRestaurantName(), cart.getRestaurantName());
            cart = new Cart(userId, itemDetails.getRestaurantId(), itemDetails.getRestaurantName());
            userCarts.put(userId, cart);
        }


        Optional<CartItem> existingItemOpt = cart.getItems().stream()
                .filter(ci -> ci.getMenuItemId().equals(addItemRequest.getMenuItemId()))
                .findFirst();

        if (existingItemOpt.isPresent()) {
            CartItem existingItem = existingItemOpt.get();
            existingItem.setQuantity(existingItem.getQuantity() + addItemRequest.getQuantity());
        } else {
            CartItem newItem = new CartItem(
                    itemDetails.getId(),
                    itemDetails.getName(),
                    addItemRequest.getQuantity(),
                    itemDetails.getPrice()
            );
            cart.getItems().add(newItem);
        }
        cart.recalculateCartTotalPrice();
        return mapCartToResponse(cart);
    }

    @Override
    public CartResponse getCart(String userId) {
        Cart cart = userCarts.get(userId);
        if (cart == null) {
            // Return an empty cart response instead of throwing an error for GET
            return new CartResponse(userId, null, null, List.of(), java.math.BigDecimal.ZERO);
        }
        return mapCartToResponse(cart);
    }

    @Override
    public CartResponse updateCartItem(String userId, Long menuItemId, UpdateCartItemRequest updateRequest) {
        Cart cart = userCarts.get(userId);
        if (cart == null) {
            throw new CartNotFoundException("Cart not found for user " + userId);
        }

        CartItem itemToUpdate = cart.getItems().stream()
                .filter(ci -> ci.getMenuItemId().equals(menuItemId))
                .findFirst()
                .orElseThrow(() -> new MenuItemNotFoundInCartException("Menu item " + menuItemId + " not found in cart."));

        itemToUpdate.setQuantity(updateRequest.getQuantity());
        cart.recalculateCartTotalPrice();
        return mapCartToResponse(cart);
    }

    @Override
    public CartResponse removeCartItem(String userId, Long menuItemId) {
        Cart cart = userCarts.get(userId);
        if (cart == null) {
            throw new CartNotFoundException("Cart not found for user " + userId);
        }

        boolean removed = cart.getItems().removeIf(ci -> ci.getMenuItemId().equals(menuItemId));
        if (!removed) {
            throw new MenuItemNotFoundInCartException("Menu item " + menuItemId + " not found in cart for removal.");
        }
        
        // If cart becomes empty, we could remove the cart itself from userCarts or keep it as an empty cart.
        // Keeping it is simpler for now. If items empty, restaurantId might become irrelevant.
        if (cart.getItems().isEmpty()) {
            cart.setRestaurantId(null);
            cart.setRestaurantName(null);
        }

        cart.recalculateCartTotalPrice();
        return mapCartToResponse(cart);
    }

    @Override
    public void clearCart(String userId) {
        Cart cart = userCarts.remove(userId);
         if (cart == null) {
            // Optionally, you could throw CartNotFoundException or just log
            logger.info("Attempted to clear a non-existent cart for user {}", userId);
            // To ensure idempotency or avoid error for clearing non-existent cart:
            // throw new CartNotFoundException("Cart not found for user " + userId + " to clear.");
        } else {
            logger.info("Cart cleared for user {}", userId);
        }
    }

    private CartResponse mapCartToResponse(Cart cart) {
        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .map(ci -> new CartItemResponse(
                        ci.getMenuItemId(),
                        ci.getMenuItemName(),
                        ci.getQuantity(),
                        ci.getUnitPrice(),
                        ci.getTotalPrice()))
                .collect(Collectors.toList());
        return new CartResponse(cart.getUserId(), cart.getRestaurantId(), cart.getRestaurantName(), itemResponses, cart.getCartTotalPrice());
    }
}