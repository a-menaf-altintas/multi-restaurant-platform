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
    public void testGetFilteredOrderHistory() {
        // Arrange
        String username = "testUser";
        Long userId = 1L;

        User user = new User();
        user.setId(userId);
        user.setUsername(username);

        Order order1 = new Order();
        order1.setId(1L);
        order1.setCustomerId(userId);
        order1.setStatus(OrderStatus.DELIVERED);

        Order order2 = new Order();
        order2.setId(2L);
        order2.setCustomerId(userId);
        order2.setStatus(OrderStatus.CONFIRMED);

        LocalDateTime startDate = LocalDateTime.now().minusDays(7);
        LocalDateTime endDate = LocalDateTime.now();
        OrderStatus status = OrderStatus.DELIVERED;

        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orderPage = new PageImpl<>(List.of(order1), pageable, 1);

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
}