// File: backend/order/src/main/java/com/multirestaurantplatform/order/service/impl/PersistentCartServiceImpl.java

package com.multirestaurantplatform.order.service.impl;

import com.multirestaurantplatform.order.dto.AddItemToCartRequest;
import com.multirestaurantplatform.order.dto.CartItemResponse;
import com.multirestaurantplatform.order.dto.CartResponse;
import com.multirestaurantplatform.order.dto.UpdateCartItemRequest;
import com.multirestaurantplatform.order.exception.CartNotFoundException;
import com.multirestaurantplatform.order.exception.CartUpdateException;
import com.multirestaurantplatform.order.exception.MenuItemNotFoundInCartException;
import com.multirestaurantplatform.order.model.cart.CartEntity;
import com.multirestaurantplatform.order.model.cart.CartItemEntity;
import com.multirestaurantplatform.order.repository.CartItemRepository;
import com.multirestaurantplatform.order.repository.CartRepository;
import com.multirestaurantplatform.order.service.CartService;
import com.multirestaurantplatform.order.service.client.MenuItemDetailsDto;
import com.multirestaurantplatform.order.service.client.MenuServiceClient;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class PersistentCartServiceImpl implements CartService {

    private static final Logger logger = LoggerFactory.getLogger(PersistentCartServiceImpl.class);

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final MenuServiceClient menuServiceClient;

    @Autowired
    public PersistentCartServiceImpl(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            MenuServiceClient menuServiceClient) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.menuServiceClient = menuServiceClient;
        logger.info("PersistentCartServiceImpl initialized. Using database persistence for carts.");
    }

    @Override
    @Transactional
    public CartResponse addItemToCart(String userId, AddItemToCartRequest addItemRequest) {
        logger.info("Adding item to cart for user: {}, menuItemId: {}, restaurantId: {}",
                userId, addItemRequest.getMenuItemId(), addItemRequest.getRestaurantId());

        // Validate the menu item details
        MenuItemDetailsDto itemDetails = menuServiceClient
                .getMenuItemDetails(addItemRequest.getMenuItemId(), addItemRequest.getRestaurantId())
                .orElseThrow(() -> new CartUpdateException("Menu item " + addItemRequest.getMenuItemId() + " not found or unavailable."));

        logger.info("Menu item details: ID={}, RestaurantID={}, Name={}, Available={}",
                itemDetails.getId(), itemDetails.getRestaurantId(), itemDetails.getName(), itemDetails.isAvailable());

        if (!itemDetails.isAvailable()) {
            throw new CartUpdateException("Menu item " + itemDetails.getName() + " is currently unavailable.");
        }

        if (!itemDetails.getRestaurantId().equals(addItemRequest.getRestaurantId())) {
            throw new CartUpdateException("Menu item " + itemDetails.getName() + " does not belong to restaurant " + addItemRequest.getRestaurantId());
        }

        // Find existing cart or create a new one
        CartEntity cart = cartRepository.findByUserId(userId)
                .orElse(new CartEntity());

        // If this is a new cart, initialize it
        if (cart.getId() == null) {
            cart.setUserId(userId);
            cart.setRestaurantId(itemDetails.getRestaurantId());
            cart.setRestaurantName(itemDetails.getRestaurantName());
            cart.setCartTotalPrice(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            // Save cart first to get an ID
            cart = cartRepository.save(cart);
        }
        // If cart exists but has different restaurant or null restaurant ID, clear it
        else if (cart.getRestaurantId() == null || !itemDetails.getRestaurantId().equals(cart.getRestaurantId())) {
            logger.info("User {} adding item from new restaurant {}. Clearing old cart from restaurant {}.",
                    userId, itemDetails.getRestaurantName(), cart.getRestaurantName());

            // Delete all cart items from the database
            cartItemRepository.deleteByCart(cart);

            // CRITICAL FIX: Clear the items list in memory AND ensure restaurant info is updated
            cart.getItems().clear();
            cart.setRestaurantId(itemDetails.getRestaurantId());
            cart.setRestaurantName(itemDetails.getRestaurantName());

            // Reset cart total price
            cart.setCartTotalPrice(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

            // Explicitly save the cart after clearing to ensure state is consistent
            cart = cartRepository.save(cart);

            // ADDITIONAL FIX: Make sure the cart items collection is initialized as empty
            if (cart.getItems() == null) {
                cart.setItems(new ArrayList<>());
            } else if (!cart.getItems().isEmpty()) {
                cart.getItems().clear(); // Extra safety to ensure items are cleared
            }
        }

        // Check if the item already exists in the cart (using the DB, not the in-memory items which might be stale)
        Optional<CartItemEntity> existingItemOpt = cartItemRepository.findByCartAndMenuItemId(cart, addItemRequest.getMenuItemId());

        if (existingItemOpt.isPresent()) {
            // Update existing item
            CartItemEntity cartItem = existingItemOpt.get();
            cartItem.setQuantity(cartItem.getQuantity() + addItemRequest.getQuantity());
            cartItem.recalculateTotalPrice();
            cartItemRepository.save(cartItem);
        } else {
            // Add new item
            CartItemEntity cartItem = new CartItemEntity(
                    itemDetails.getId(),
                    itemDetails.getName(),
                    addItemRequest.getQuantity(),
                    itemDetails.getPrice()
            );
            cartItem.setCart(cart);
            cartItemRepository.save(cartItem);

            // CRITICAL FIX: Make sure the item is properly added to the cart's item collection
            // It's possible the items collection is out of sync with the database
            // after clearing and saving
            cart.getItems().add(cartItem);
        }

        // Recalculate cart total
        cart.recalculateCartTotalPrice();
        cart = cartRepository.save(cart);

        // Log cart state before returning for debugging
        logger.debug("Cart after adding item - userId: {}, restaurantId: {}, items count: {}",
                cart.getUserId(), cart.getRestaurantId(), cart.getItems().size());

        return mapCartEntityToResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse getCart(String userId) {
        CartEntity cart = cartRepository.findByUserId(userId)
                .orElse(null);

        if (cart == null) {
            // Return an empty cart response instead of throwing an error for GET
            return new CartResponse(userId, null, null, List.of(), BigDecimal.ZERO);
        }

        return mapCartEntityToResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse updateCartItem(String userId, Long menuItemId, UpdateCartItemRequest updateRequest) {
        CartEntity cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found for user " + userId));

        CartItemEntity itemToUpdate = cartItemRepository.findByCartAndMenuItemId(cart, menuItemId)
                .orElseThrow(() -> new MenuItemNotFoundInCartException("Menu item " + menuItemId + " not found in cart."));

        itemToUpdate.setQuantity(updateRequest.getQuantity());
        itemToUpdate.recalculateTotalPrice();
        cartItemRepository.save(itemToUpdate);

        // Recalculate cart total
        cart.recalculateCartTotalPrice();
        cart = cartRepository.save(cart);

        return mapCartEntityToResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse removeCartItem(String userId, Long menuItemId) {
        CartEntity cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found for user " + userId));

        CartItemEntity itemToRemove = cartItemRepository.findByCartAndMenuItemId(cart, menuItemId)
                .orElseThrow(() -> new MenuItemNotFoundInCartException("Menu item " + menuItemId + " not found in cart for removal."));

        // Remove the item from the cart's items collection
        cart.getItems().remove(itemToRemove);

        // Delete the item entity from the database
        cartItemRepository.delete(itemToRemove);

        // If cart becomes empty after removing the item, reset restaurant info
        if (cart.getItems().isEmpty()) {
            cart.setRestaurantId(null);
            cart.setRestaurantName(null);
        }

        // Recalculate cart total and ensure proper scaling
        cart.recalculateCartTotalPrice();
        cart.setCartTotalPrice(cart.getCartTotalPrice().setScale(2, RoundingMode.HALF_UP));
        cart = cartRepository.save(cart);

        return mapCartEntityToResponse(cart);
    }

    @Override
    @Transactional
    public void clearCart(String userId) {
        CartEntity cart = cartRepository.findByUserId(userId)
                .orElse(null);

        if (cart == null) {
            logger.info("Attempted to clear a non-existent cart for user {}", userId);
            return;
        }

        // Delete all cart items from the database
        cartItemRepository.deleteByCart(cart);

        // Reset the cart but keep the entity (soft clear)
        cart.getItems().clear();
        cart.setRestaurantId(null);
        cart.setRestaurantName(null);
        cart.setCartTotalPrice(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        cartRepository.save(cart);

        logger.info("Cart cleared for user {}", userId);
    }

    private CartItemResponse mapCartItemEntityToResponse(CartItemEntity cartItem) {
        return new CartItemResponse(
                cartItem.getMenuItemId(),
                cartItem.getMenuItemName(),
                cartItem.getQuantity(),
                cartItem.getUnitPrice(),
                cartItem.getTotalPrice()
        );
    }

    private CartResponse mapCartEntityToResponse(CartEntity cart) {
        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .map(this::mapCartItemEntityToResponse)
                .collect(Collectors.toList());

        // Ensure the cart total has the right scale for consistent comparison
        BigDecimal formattedTotal = cart.getCartTotalPrice().setScale(2, RoundingMode.HALF_UP);

        // Add debug logging to help diagnose issues
        logger.debug("Mapping cart to response - Cart ID: {}, User ID: {}, Restaurant ID: {}, Items count: {}",
                cart.getId(), cart.getUserId(), cart.getRestaurantId(), itemResponses.size());

        return new CartResponse(
                cart.getUserId(),
                cart.getRestaurantId(),
                cart.getRestaurantName(),
                itemResponses,
                formattedTotal
        );
    }
}