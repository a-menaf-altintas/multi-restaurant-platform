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
            description = "Allows a RESTAURANT_ADMIN to confirm a 'PLACED' order, changing its status to 'CONFIRMED'. " +
                    "The admin must be associated with the restaurant of the order.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order confirmed successfully",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request (e.g., order not in PLACED state)"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token is missing or invalid"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - User does not have necessary permissions or is not admin of the order's restaurant"),
                    @ApiResponse(responseCode = "404", description = "Order or associated Restaurant not found")
            })
    public ResponseEntity<OrderResponse> confirmOrder(
            @Parameter(description = "ID of the order to be confirmed") @PathVariable Long orderId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        LOGGER.info("API call to confirm order ID: {} by user: {}", orderId, userDetails.getUsername());
        Order confirmedOrder = orderService.confirmOrder(orderId, userDetails);
        OrderResponse responseDto = OrderResponse.fromEntity(confirmedOrder);
        LOGGER.info("Order ID: {} confirmed. Status: {}", responseDto.getId(), responseDto.getStatus());
        return ResponseEntity.ok(responseDto);
    }

    @PutMapping("/{orderId}/prepare")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Mark order as preparing",
            description = "Allows a RESTAURANT_ADMIN to mark a 'CONFIRMED' order as 'PREPARING'. " +
                    "The admin must be associated with the restaurant of the order.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order status updated to PREPARING",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request (e.g., order not in CONFIRMED state)"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token is missing or invalid"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - User does not have necessary permissions or is not admin of the order's restaurant"),
                    @ApiResponse(responseCode = "404", description = "Order or associated Restaurant not found")
            })
    public ResponseEntity<OrderResponse> markAsPreparing(
            @Parameter(description = "ID of the order to be marked as preparing") @PathVariable Long orderId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        LOGGER.info("API call to mark order ID: {} as PREPARING by user: {}", orderId, userDetails.getUsername());
        Order preparingOrder = orderService.markAsPreparing(orderId, userDetails);
        OrderResponse responseDto = OrderResponse.fromEntity(preparingOrder);
        LOGGER.info("Order ID: {} marked as PREPARING. Status: {}", responseDto.getId(), responseDto.getStatus());
        return ResponseEntity.ok(responseDto);
    }

    // TODO: Add other endpoints for order lifecycle:
    // - GET /api/v1/orders/{orderId} (for CUSTOMER, RESTAURANT_ADMIN, ADMIN)
    // - GET /api/v1/restaurant/{restaurantId}/orders (for RESTAURANT_ADMIN of that restaurant, ADMIN)
    // - GET /api/v1/users/{userId}/orders (for CUSTOMER themselves, or ADMIN)
    // - POST /api/v1/orders (from cart to create order - for CUSTOMER)
    // - PUT /api/v1/orders/{orderId}/ready (for RESTAURANT_ADMIN)
    // - PUT /api/v1/orders/{orderId}/deliver (for RESTAURANT_ADMIN or DELIVERY_PERSON)
    // - PUT /api/v1/orders/{orderId}/cancel (for CUSTOMER under certain conditions, or RESTAURANT_ADMIN/ADMIN)
}
