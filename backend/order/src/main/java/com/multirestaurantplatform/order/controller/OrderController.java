// File: backend/order/src/main/java/com/multirestaurantplatform/order/controller/OrderController.java
package com.multirestaurantplatform.order.controller;

import com.multirestaurantplatform.order.dto.OrderResponse;
import com.multirestaurantplatform.order.model.Order;
import com.multirestaurantplatform.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1") // Adjusted base path for user-specific cart endpoint
@RequiredArgsConstructor
@Tag(name = "Order Management", description = "APIs for managing customer orders and their lifecycle.")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderController.class);
    private final OrderService orderService;

    // Endpoint to place an order from a user's cart
    @PostMapping("/users/{userId}/orders/place-from-cart")
    // CUSTOMER can place for their own userId. ADMIN can place for any userId.
    // userId here is expected to be the username for consistency with CartService.
    @PreAuthorize("hasRole('ADMIN') or (hasRole('CUSTOMER') and #userId == principal.username)")
    @Operation(summary = "Place an order from user's cart",
            description = "Creates a new order from the specified user's current shopping cart. " +
                    "The authenticated user must be the owner of the cart or an ADMIN. " +
                    "The cart will be cleared upon successful order placement.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Order placed successfully",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request (e.g., cart empty, cart not associated with restaurant)"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token is missing or invalid"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - User not authorized to place order for this cart"),
                    @ApiResponse(responseCode = "404", description = "User or Cart not found")
            })
    public ResponseEntity<OrderResponse> placeOrderFromCart(
            @Parameter(description = "Username of the user whose cart is to be converted into an order.") @PathVariable String userId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails principal) {
        // Note: If userId is a numeric ID, the @PreAuthorize and service logic would need adjustment.
        // Assuming userId is username here.
        LOGGER.info("API call to place order from cart for user: {} by principal: {}", userId, principal.getUsername());
        Order placedOrder = orderService.placeOrderFromCart(userId, principal);
        OrderResponse responseDto = OrderResponse.fromEntity(placedOrder);
        LOGGER.info("Order ID: {} placed successfully from cart for user: {}", responseDto.getId(), userId);
        return new ResponseEntity<>(responseDto, HttpStatus.CREATED);
    }


    // --- Existing Order Status Transition Endpoints ---
    @PutMapping("/orders/{orderId}/confirm")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Confirm an order")
    public ResponseEntity<OrderResponse> confirmOrder(
            @Parameter(description = "ID of the order to be confirmed") @PathVariable Long orderId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        LOGGER.info("API call to confirm order ID: {} by user: {}", orderId, userDetails.getUsername());
        Order confirmedOrder = orderService.confirmOrder(orderId, userDetails);
        return ResponseEntity.ok(OrderResponse.fromEntity(confirmedOrder));
    }

    @PutMapping("/orders/{orderId}/prepare")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Mark order as preparing")
    public ResponseEntity<OrderResponse> markAsPreparing(
            @Parameter(description = "ID of the order to be marked as preparing") @PathVariable Long orderId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        LOGGER.info("API call to mark order ID: {} as PREPARING by user: {}", orderId, userDetails.getUsername());
        Order preparingOrder = orderService.markAsPreparing(orderId, userDetails);
        return ResponseEntity.ok(OrderResponse.fromEntity(preparingOrder));
    }

    @PutMapping("/orders/{orderId}/ready-for-pickup")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Mark order as ready for pickup")
    public ResponseEntity<OrderResponse> markAsReadyForPickup(
            @Parameter(description = "ID of the order to be marked as ready for pickup") @PathVariable Long orderId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        LOGGER.info("API call to mark order ID: {} as READY_FOR_PICKUP by user: {}", orderId, userDetails.getUsername());
        Order readyOrder = orderService.markAsReadyForPickup(orderId, userDetails);
        return ResponseEntity.ok(OrderResponse.fromEntity(readyOrder));
    }

    @PutMapping("/orders/{orderId}/picked-up")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Mark order as picked up (Delivered)")
    public ResponseEntity<OrderResponse> markAsPickedUp(
            @Parameter(description = "ID of the order to be marked as picked up") @PathVariable Long orderId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        LOGGER.info("API call to mark order ID: {} as DELIVERED (picked up) by user: {}", orderId, userDetails.getUsername());
        Order pickedUpOrder = orderService.markAsPickedUp(orderId, userDetails);
        return ResponseEntity.ok(OrderResponse.fromEntity(pickedUpOrder));
    }

    @PutMapping("/orders/{orderId}/out-for-delivery")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Mark order as out for delivery")
    public ResponseEntity<OrderResponse> markAsOutForDelivery(
            @Parameter(description = "ID of the order to be marked as out for delivery") @PathVariable Long orderId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        LOGGER.info("API call to mark order ID: {} as OUT_FOR_DELIVERY by user: {}", orderId, userDetails.getUsername());
        Order outForDeliveryOrder = orderService.markAsOutForDelivery(orderId, userDetails);
        return ResponseEntity.ok(OrderResponse.fromEntity(outForDeliveryOrder));
    }

    @PutMapping("/orders/{orderId}/delivery-completed")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Mark order delivery as completed (Delivered)")
    public ResponseEntity<OrderResponse> completeDelivery(
            @Parameter(description = "ID of the order whose delivery is completed") @PathVariable Long orderId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        LOGGER.info("API call to complete delivery for order ID: {} by user: {}", orderId, userDetails.getUsername());
        Order deliveredOrder = orderService.completeDelivery(orderId, userDetails);
        return ResponseEntity.ok(OrderResponse.fromEntity(deliveredOrder));
    }
}
