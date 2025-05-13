package com.multirestaurantplatform.order.controller;

import com.multirestaurantplatform.order.dto.OrderResponse;
import com.multirestaurantplatform.order.model.Order;
import com.multirestaurantplatform.order.model.OrderStatus;
import com.multirestaurantplatform.order.service.OrderService;
import com.multirestaurantplatform.security.model.User;
import com.multirestaurantplatform.security.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Map;
import com.multirestaurantplatform.order.dto.OrderStatisticsResponseDto;

@ExtendWith(MockitoExtension.class)
public class OrderControllerTest {

    @Mock
    private OrderService orderService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserDetails principal;

    @InjectMocks
    private OrderController orderController;

    @Test
    public void testGetOrderHistory() {
        // Arrange
        String username = "testUser";
        Long userId = 1L;

        User user = new User();
        user.setId(userId);
        user.setUsername(username);

        Order order1 = new Order();
        order1.setId(1L);
        order1.setCustomerId(userId);

        Order order2 = new Order();
        order2.setId(2L);
        order2.setCustomerId(userId);

        List<Order> orderList = Arrays.asList(order1, order2);

        when(principal.getUsername()).thenReturn(username);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(orderService.findOrdersByCustomerId(userId)).thenReturn(orderList);

        // Act
        ResponseEntity<List<OrderResponse>> response = orderController.getOrderHistory(username, principal);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());

        // Verify
        verify(userRepository, times(1)).findByUsername(username);
        verify(orderService, times(1)).findOrdersByCustomerId(userId);
    }
    @Test
    public void testGetPaginatedOrderHistory() {
        // Arrange
        String username = "testUser";
        Long userId = 1L;

        User user = new User();
        user.setId(userId);
        user.setUsername(username);

        List<Order> orders = Arrays.asList(
                createSampleOrder(1L, userId, OrderStatus.DELIVERED),
                createSampleOrder(2L, userId, OrderStatus.CONFIRMED)
        );

        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orderPage = new PageImpl<>(orders, pageable, orders.size());

        when(principal.getUsername()).thenReturn(username);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(orderService.findOrdersByCustomerId(userId, pageable)).thenReturn(orderPage);

        // Act
        ResponseEntity<Page<OrderResponse>> response = orderController.getPaginatedOrderHistory(
                username, 0, 10, "createdAt", "DESC", principal);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().getTotalElements());

        // Verify
        verify(userRepository).findByUsername(username);
        verify(orderService).findOrdersByCustomerId(userId, pageable);
    }

    @Test
    public void testGetFilteredOrderHistory() {
        // Arrange
        String username = "testUser";
        Long userId = 1L;

        User user = new User();
        user.setId(userId);
        user.setUsername(username);

        List<Order> orders = List.of(createSampleOrder(1L, userId, OrderStatus.DELIVERED));

        LocalDateTime startDate = LocalDateTime.now().minusDays(7);
        LocalDateTime endDate = LocalDateTime.now();
        OrderStatus status = OrderStatus.DELIVERED;

        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orderPage = new PageImpl<>(orders, pageable, orders.size());

        when(principal.getUsername()).thenReturn(username);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(orderService.findFilteredOrdersByCustomerId(userId, status, startDate, endDate, pageable))
                .thenReturn(orderPage);

        // Act
        ResponseEntity<Page<OrderResponse>> response = orderController.getFilteredOrderHistory(
                username, status, startDate, endDate, 0, 10, "createdAt", "DESC", principal);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getTotalElements());
        assertEquals(OrderStatus.DELIVERED, response.getBody().getContent().get(0).getStatus());

        // Verify
        verify(userRepository).findByUsername(username);
        verify(orderService).findFilteredOrdersByCustomerId(userId, status, startDate, endDate, pageable);
    }

    @Test
    public void testGetOrderStatistics() {
        // Arrange
        String username = "testUser";
        Long userId = 1L;

        User user = new User();
        user.setId(userId);
        user.setUsername(username);

        OrderStatisticsResponseDto statistics = new OrderStatisticsResponseDto();
        statistics.setTotalOrders(5L);
        statistics.setTotalSpent(new BigDecimal("150.50"));
        statistics.setAverageOrderAmount(new BigDecimal("30.10"));
        statistics.setOrdersByStatus(Map.of(
                "DELIVERED", 3L,
                "CONFIRMED", 2L
        ));
        statistics.setFirstOrderDate(LocalDateTime.now().minusDays(30));
        statistics.setLastOrderDate(LocalDateTime.now().minusDays(2));
        statistics.setRestaurantCount(2L);
        statistics.setMostOrderedRestaurantId(101L);
        statistics.setMostOrderedRestaurantName("Test Restaurant");
        statistics.setMostOrderedRestaurantOrderCount(3L);

        when(principal.getUsername()).thenReturn(username);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(orderService.getOrderStatisticsForCustomer(userId)).thenReturn(statistics);

        // Act
        ResponseEntity<OrderStatisticsResponseDto> response = orderController.getOrderStatistics(username, principal);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(5L, response.getBody().getTotalOrders());
        assertEquals(new BigDecimal("150.50"), response.getBody().getTotalSpent());
        assertEquals(2L, response.getBody().getOrdersByStatus().size());

        // Verify
        verify(userRepository).findByUsername(username);
        verify(orderService).getOrderStatisticsForCustomer(userId);
    }

    // Helper method to create sample orders for testing
    private Order createSampleOrder(Long id, Long customerId, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setCustomerId(customerId);
        order.setStatus(status);
        order.setTotalPrice(new BigDecimal("30.00"));
        order.setCreatedAt(LocalDateTime.now().minusDays(id)); // Different creation dates
        return order;
    }
}