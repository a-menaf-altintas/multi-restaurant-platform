package com.multirestaurantplatform.order.controller;

import com.multirestaurantplatform.order.dto.AddItemToCartRequest;
import com.multirestaurantplatform.order.dto.CartResponse;
import com.multirestaurantplatform.order.dto.UpdateCartItemRequest;
import com.multirestaurantplatform.order.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Note: Ensure spring-security-core is a dependency if @AuthenticationPrincipal is to be used.
// import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/v1/users/{userId}/cart") // Using userId in path for explicit context
@Tag(name = "Cart API", description = "Operations pertaining to user shopping cart")
public class CartController {

    private final CartService cartService;

    @Autowired
    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @Operation(summary = "Add an item to the user's cart",
            description = "Adds a specified quantity of a menu item to the cart. If the cart or item does not exist, it will be created. If adding from a different restaurant, current cart may be cleared.")
    @ApiResponse(responseCode = "200", description = "Item added successfully, returns updated cart")
    @ApiResponse(responseCode = "400", description = "Invalid request (e.g., item not found, validation error)")
    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItemToCart(
            @Parameter(description = "ID of the user whose cart is being modified") @PathVariable("userId") String userId, // Explicitly named
            @Valid @RequestBody AddItemToCartRequest addItemRequest) {
        // In a real app with Spring Security, you might get userId from @AuthenticationPrincipal
        CartResponse cartResponse = cartService.addItemToCart(userId, addItemRequest);
        return ResponseEntity.ok(cartResponse);
    }

    @Operation(summary = "Get the user's current cart")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved cart")
    @GetMapping
    public ResponseEntity<CartResponse> getCart(
            @Parameter(description = "ID of the user whose cart is being retrieved") @PathVariable("userId") String userId) { // Explicitly named
        CartResponse cartResponse = cartService.getCart(userId);
        return ResponseEntity.ok(cartResponse);
    }

    @Operation(summary = "Update quantity of an item in the cart",
            description = "Updates the quantity of a specific menu item already in the cart.")
    @ApiResponse(responseCode = "200", description = "Item updated successfully, returns updated cart")
    @ApiResponse(responseCode = "404", description = "Cart or menu item not found in cart")
    @PutMapping("/items/{menuItemId}")
    public ResponseEntity<CartResponse> updateCartItem(
            @Parameter(description = "ID of the user") @PathVariable("userId") String userId, // Explicitly named
            @Parameter(description = "ID of the menu item to update") @PathVariable("menuItemId") Long menuItemId, // Explicitly named
            @Valid @RequestBody UpdateCartItemRequest updateRequest) {
        CartResponse cartResponse = cartService.updateCartItem(userId, menuItemId, updateRequest);
        return ResponseEntity.ok(cartResponse);
    }

    @Operation(summary = "Remove an item from the cart")
    @ApiResponse(responseCode = "200", description = "Item removed successfully, returns updated cart")
    @ApiResponse(responseCode = "404", description = "Cart or menu item not found in cart")
    @DeleteMapping("/items/{menuItemId}")
    public ResponseEntity<CartResponse> removeCartItem(
            @Parameter(description = "ID of the user") @PathVariable("userId") String userId, // Explicitly named
            @Parameter(description = "ID of the menu item to remove") @PathVariable("menuItemId") Long menuItemId) { // Explicitly named
        CartResponse cartResponse = cartService.removeCartItem(userId, menuItemId);
        return ResponseEntity.ok(cartResponse);
    }

    @Operation(summary = "Clear all items from the user's cart")
    @ApiResponse(responseCode = "204", description = "Cart cleared successfully")
    @DeleteMapping
    public ResponseEntity<Void> clearCart(
            @Parameter(description = "ID of the user whose cart is being cleared") @PathVariable("userId") String userId) { // Explicitly named
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }
}
