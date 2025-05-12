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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Order Management", description = "APIs for managing customer orders and their lifecycle.")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderController.class);
    private final OrderService orderService;

    @PutMapping("/{orderId}/confirm")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Confirm an order",
            description = "Allows a RESTAURANT_ADMIN to confirm a 'PLACED' order, changing its status to 'CONFIRMED'.")
    public ResponseEntity<OrderResponse> confirmOrder(
            @Parameter(description = "ID of the order to be confirmed") @PathVariable Long orderId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        LOGGER.info("API call to confirm order ID: {} by user: {}", orderId, userDetails.getUsername());
        Order confirmedOrder = orderService.confirmOrder(orderId, userDetails);
        return ResponseEntity.ok(OrderResponse.fromEntity(confirmedOrder));
    }

    @PutMapping("/{orderId}/prepare")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Mark order as preparing",
            description = "Allows a RESTAURANT_ADMIN to mark a 'CONFIRMED' order as 'PREPARING'.")
    public ResponseEntity<OrderResponse> markAsPreparing(
            @Parameter(description = "ID of the order to be marked as preparing") @PathVariable Long orderId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        LOGGER.info("API call to mark order ID: {} as PREPARING by user: {}", orderId, userDetails.getUsername());
        Order preparingOrder = orderService.markAsPreparing(orderId, userDetails);
        return ResponseEntity.ok(OrderResponse.fromEntity(preparingOrder));
    }

    @PutMapping("/{orderId}/ready-for-pickup")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Mark order as ready for pickup",
            description = "Allows a RESTAURANT_ADMIN to mark a 'PREPARING' order as 'READY_FOR_PICKUP'.")
    public ResponseEntity<OrderResponse> markAsReadyForPickup(
            @Parameter(description = "ID of the order to be marked as ready for pickup") @PathVariable Long orderId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        LOGGER.info("API call to mark order ID: {} as READY_FOR_PICKUP by user: {}", orderId, userDetails.getUsername());
        Order readyOrder = orderService.markAsReadyForPickup(orderId, userDetails);
        return ResponseEntity.ok(OrderResponse.fromEntity(readyOrder));
    }

    @PutMapping("/{orderId}/picked-up")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Mark order as picked up (Delivered)",
            description = "Allows a RESTAURANT_ADMIN to mark an order that is 'READY_FOR_PICKUP' as 'DELIVERED'.")
    public ResponseEntity<OrderResponse> markAsPickedUp(
            @Parameter(description = "ID of the order to be marked as picked up") @PathVariable Long orderId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        LOGGER.info("API call to mark order ID: {} as DELIVERED (picked up) by user: {}", orderId, userDetails.getUsername());
        Order pickedUpOrder = orderService.markAsPickedUp(orderId, userDetails);
        return ResponseEntity.ok(OrderResponse.fromEntity(pickedUpOrder));
    }

    @PutMapping("/{orderId}/out-for-delivery")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Mark order as out for delivery",
            description = "Allows a RESTAURANT_ADMIN to mark an order (typically 'READY_FOR_PICKUP') as 'OUT_FOR_DELIVERY'.")
    public ResponseEntity<OrderResponse> markAsOutForDelivery(
            @Parameter(description = "ID of the order to be marked as out for delivery") @PathVariable Long orderId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        LOGGER.info("API call to mark order ID: {} as OUT_FOR_DELIVERY by user: {}", orderId, userDetails.getUsername());
        Order outForDeliveryOrder = orderService.markAsOutForDelivery(orderId, userDetails);
        return ResponseEntity.ok(OrderResponse.fromEntity(outForDeliveryOrder));
    }

    @PutMapping("/{orderId}/delivery-completed")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')") // Or potentially a DELIVERY_PERSON_ROLE later
    @Operation(summary = "Mark order delivery as completed (Delivered)",
            description = "Allows a RESTAURANT_ADMIN (or future DELIVERY_PERSON) to mark an 'OUT_FOR_DELIVERY' order as 'DELIVERED'.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order status updated to DELIVERED",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request (e.g., order not in OUT_FOR_DELIVERY state)"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token is missing or invalid"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - User does not have necessary permissions"),
                    @ApiResponse(responseCode = "404", description = "Order or associated Restaurant not found")
            })
    public ResponseEntity<OrderResponse> completeDelivery(
            @Parameter(description = "ID of the order whose delivery is completed") @PathVariable Long orderId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        LOGGER.info("API call to complete delivery for order ID: {} by user: {}", orderId, userDetails.getUsername());
        Order deliveredOrder = orderService.completeDelivery(orderId, userDetails);
        OrderResponse responseDto = OrderResponse.fromEntity(deliveredOrder);
        LOGGER.info("Order ID: {} delivery completed. Status: {}", responseDto.getId(), responseDto.getStatus());
        return ResponseEntity.ok(responseDto);
    }

    // TODO: Add other endpoints for order lifecycle:
    // - GET /api/v1/orders/{orderId}
    // - GET /api/v1/restaurant/{restaurantId}/orders
    // - GET /api/v1/users/{userId}/orders
    // - POST /api/v1/orders (from cart to create order - for CUSTOMER)
    // - PUT /api/v1/orders/{orderId}/cancel
}
