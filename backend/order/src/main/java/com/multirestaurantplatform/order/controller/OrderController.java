// File: backend/order/src/main/java/com/multirestaurantplatform/order/controller/OrderController.java
package com.multirestaurantplatform.order.controller;

import com.multirestaurantplatform.common.exception.BadRequestException;
import com.multirestaurantplatform.common.exception.ResourceNotFoundException;
import com.multirestaurantplatform.order.dto.OrderResponse;
import com.multirestaurantplatform.order.dto.OrderStatisticsResponseDto;
import com.multirestaurantplatform.order.dto.PlaceOrderRequestDto; // For delivery address
import com.multirestaurantplatform.order.model.Order;
import com.multirestaurantplatform.order.model.OrderStatus;
import com.multirestaurantplatform.order.service.OrderService;
import com.multirestaurantplatform.security.model.User;
import com.multirestaurantplatform.security.repository.UserRepository;

// Import ErrorResponse from the API module (as per user's build.gradle modification)
import com.multirestaurantplatform.common.dto.error.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Order Management", description = "APIs for managing customer orders and their lifecycle.")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderController.class);
    private final OrderService orderService;
    private final UserRepository userRepository;

    @PostMapping("/users/{userId}/orders/place-from-cart")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('CUSTOMER') and #userId == principal.username)")
    @Operation(summary = "Place an order from user's cart",
            description = "Creates a new order from the specified user's current shopping cart. " +
                    "The authenticated user must be the owner of the cart or an ADMIN. " +
                    "The cart will be cleared upon successful order placement. " +
                    "Delivery address details can be provided in the request body for delivery orders.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Order placed successfully",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request (e.g., cart empty, validation error on address)",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token is missing or invalid",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden - User not authorized to place order for this cart",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "User or Cart not found",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<OrderResponse> placeOrderFromCart(
            @Parameter(description = "Username of the user whose cart is to be converted into an order.") @PathVariable String userId,
            // This parameter now accepts the PlaceOrderRequestDto
            @Parameter(description = "Order details including delivery address (optional for pickup orders).") @Valid @RequestBody(required = false) PlaceOrderRequestDto placeOrderRequestDto,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails principal) {
        LOGGER.info("API call to place order from cart for user: {} by principal: {}. With request DTO: {}", userId, principal.getUsername(), placeOrderRequestDto != null);
        // The placeOrderRequestDto is now passed to the service layer
        Order placedOrder = orderService.placeOrderFromCart(userId, principal, placeOrderRequestDto);
        OrderResponse responseDto = OrderResponse.fromEntity(placedOrder);
        LOGGER.info("Order ID: {} placed successfully from cart for user: {}", responseDto.getId(), userId);
        return new ResponseEntity<>(responseDto, HttpStatus.CREATED);
    }

    // --- Existing Order Status Transition Endpoints ---
    // (Ensure @ApiResponse annotations in these methods also use the api.dto.error.ErrorResponse if they specify error content)
    @PutMapping("/orders/{orderId}/confirm")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Confirm an order",
            responses = { @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = OrderResponse.class))),
                    @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description="Illegal State", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<OrderResponse> confirmOrder(
            @Parameter(description = "ID of the order to be confirmed") @PathVariable Long orderId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        LOGGER.info("API call to confirm order ID: {} by user: {}", orderId, userDetails.getUsername());
        Order confirmedOrder = orderService.confirmOrder(orderId, userDetails);
        return ResponseEntity.ok(OrderResponse.fromEntity(confirmedOrder));
    }

    @PutMapping("/orders/{orderId}/prepare")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Mark order as preparing",
            responses = { @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = OrderResponse.class))),
                    @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description="Illegal State", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<OrderResponse> markAsPreparing(
            @Parameter(description = "ID of the order to be marked as preparing") @PathVariable Long orderId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        LOGGER.info("API call to mark order ID: {} as PREPARING by user: {}", orderId, userDetails.getUsername());
        Order preparingOrder = orderService.markAsPreparing(orderId, userDetails);
        return ResponseEntity.ok(OrderResponse.fromEntity(preparingOrder));
    }

    @PutMapping("/orders/{orderId}/ready-for-pickup")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Mark order as ready for pickup",
            responses = { @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = OrderResponse.class))),
                    @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description="Illegal State", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<OrderResponse> markAsReadyForPickup(
            @Parameter(description = "ID of the order to be marked as ready for pickup") @PathVariable Long orderId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        LOGGER.info("API call to mark order ID: {} as READY_FOR_PICKUP by user: {}", orderId, userDetails.getUsername());
        Order readyOrder = orderService.markAsReadyForPickup(orderId, userDetails);
        return ResponseEntity.ok(OrderResponse.fromEntity(readyOrder));
    }

    @PutMapping("/orders/{orderId}/picked-up")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Mark order as picked up (Delivered for pickup orders)",
            responses = { @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = OrderResponse.class))),
                    @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description="Illegal State", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<OrderResponse> markAsPickedUp(
            @Parameter(description = "ID of the order to be marked as picked up") @PathVariable Long orderId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        LOGGER.info("API call to mark order ID: {} as DELIVERED (picked up) by user: {}", orderId, userDetails.getUsername());
        Order pickedUpOrder = orderService.markAsPickedUp(orderId, userDetails);
        return ResponseEntity.ok(OrderResponse.fromEntity(pickedUpOrder));
    }

    @PutMapping("/orders/{orderId}/out-for-delivery")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Mark order as out for delivery",
            responses = { @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = OrderResponse.class))),
                    @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description="Illegal State", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<OrderResponse> markAsOutForDelivery(
            @Parameter(description = "ID of the order to be marked as out for delivery") @PathVariable Long orderId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        LOGGER.info("API call to mark order ID: {} as OUT_FOR_DELIVERY by user: {}", orderId, userDetails.getUsername());
        Order outForDeliveryOrder = orderService.markAsOutForDelivery(orderId, userDetails);
        return ResponseEntity.ok(OrderResponse.fromEntity(outForDeliveryOrder));
    }

    @PutMapping("/orders/{orderId}/delivery-completed")
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Operation(summary = "Mark order delivery as completed (Delivered for delivery orders)",
            responses = { @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = OrderResponse.class))),
                    @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description="Illegal State", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<OrderResponse> completeDelivery(
            @Parameter(description = "ID of the order whose delivery is completed") @PathVariable Long orderId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        LOGGER.info("API call to complete delivery for order ID: {} by user: {}", orderId, userDetails.getUsername());
        Order deliveredOrder = orderService.completeDelivery(orderId, userDetails);
        return ResponseEntity.ok(OrderResponse.fromEntity(deliveredOrder));
    }

    @GetMapping("/users/{userId}/orders")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('CUSTOMER') and #userId == principal.username)")
    @Operation(summary = "Get order history for a user",
            description = "Retrieves all orders placed by the specified user. " +
                    "The authenticated user must be the owner of the orders or an ADMIN.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order history retrieved successfully"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "User not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<List<OrderResponse>> getOrderHistory(
            @Parameter(description = "Username of the user whose order history is to be retrieved") @PathVariable String userId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails principal) {
        LOGGER.info("API call to get order history for user: {} by principal: {}", userId, principal.getUsername());
        User user = userRepository.findByUsername(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + userId));
        List<Order> orders = orderService.findOrdersByCustomerId(user.getId());
        List<OrderResponse> responseList = orders.stream()
                .map(OrderResponse::fromEntity)
                .collect(Collectors.toList());
        LOGGER.info("Retrieved {} orders for user: {}", responseList.size(), userId);
        return ResponseEntity.ok(responseList);
    }

    @GetMapping("/users/{userId}/orders/paged")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('CUSTOMER') and #userId == principal.username)")
    @Operation(summary = "Get paginated order history for a user",
            description = "Retrieves paginated orders placed by the specified user. " +
                    "The authenticated user must be the owner of the orders or an ADMIN.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Paginated order history retrieved successfully"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "User not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<Page<OrderResponse>> getPaginatedOrderHistory(
            @Parameter(description = "Username of the user") @PathVariable String userId,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (ASC or DESC)") @RequestParam(defaultValue = "DESC") String direction,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails principal) {
        LOGGER.info("API call to get paginated order history for user: {} by principal: {}, page: {}, size: {}",
                userId, principal.getUsername(), page, size);
        User user = userRepository.findByUsername(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + userId));
        Sort.Direction sortDirection = Sort.Direction.fromString(direction);
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        Page<Order> ordersPage = orderService.findOrdersByCustomerId(user.getId(), pageable);
        Page<OrderResponse> responsePage = ordersPage.map(OrderResponse::fromEntity);
        LOGGER.info("Retrieved page {} of {} for user: {}, total elements: {}",
                responsePage.getNumber(), responsePage.getTotalPages(), userId, responsePage.getTotalElements());
        return ResponseEntity.ok(responsePage);
    }

    @GetMapping("/users/{userId}/orders/filtered")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('CUSTOMER') and #userId == principal.username)")
    @Operation(summary = "Get filtered order history for a user",
            description = "Retrieves orders placed by the specified user with optional filtering by status and date range. " +
                    "The authenticated user must be the owner of the orders or an ADMIN.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Filtered order history retrieved successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid filter parameters", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "User not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<Page<OrderResponse>> getFilteredOrderHistory(
            @Parameter(description = "Username of the user") @PathVariable String userId,
            @Parameter(description = "Filter by order status") @RequestParam(required = false) OrderStatus status,
            @Parameter(description = "Filter by start date (ISO format)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "Filter by end date (ISO format)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (ASC or DESC)") @RequestParam(defaultValue = "DESC") String direction,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails principal) {
        LOGGER.info("API call to get filtered order history for user: {} by principal: {}, status: {}, startDate: {}, endDate: {}, page: {}, size: {}",
                userId, principal.getUsername(), status, startDate, endDate, page, size);
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BadRequestException("Start date cannot be after end date");
        }
        User user = userRepository.findByUsername(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + userId));
        Sort.Direction sortDirection = Sort.Direction.fromString(direction);
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        Page<Order> ordersPage = orderService.findFilteredOrdersByCustomerId(user.getId(), status, startDate, endDate, pageable);
        Page<OrderResponse> responsePage = ordersPage.map(OrderResponse::fromEntity);
        LOGGER.info("Retrieved filtered page {} of {} for user: {}, total elements: {}",
                responsePage.getNumber(), responsePage.getTotalPages(), userId, responsePage.getTotalElements());
        return ResponseEntity.ok(responsePage);
    }

    @GetMapping("/users/{userId}/orders/statistics")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('CUSTOMER') and #userId == principal.username)")
    @Operation(summary = "Get order statistics for a user",
            description = "Retrieves comprehensive order statistics for the specified user. " +
                    "The authenticated user must be the owner of the orders or an ADMIN.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order statistics retrieved successfully"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "User not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<OrderStatisticsResponseDto> getOrderStatistics(
            @Parameter(description = "Username of the user") @PathVariable String userId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails principal) {
        LOGGER.info("API call to get order statistics for user: {} by principal: {}", userId, principal.getUsername());
        User user = userRepository.findByUsername(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + userId));
        OrderStatisticsResponseDto statistics = orderService.getOrderStatisticsForCustomer(user.getId());
        LOGGER.info("Retrieved order statistics for user: {}, total orders: {}", userId, statistics.getTotalOrders());
        return ResponseEntity.ok(statistics);
    }
}
