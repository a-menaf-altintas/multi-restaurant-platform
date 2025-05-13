package com.multirestaurantplatform.api.controller;

import com.multirestaurantplatform.order.dto.OrderStatisticsResponseDto;
import com.multirestaurantplatform.order.model.Order;
import com.multirestaurantplatform.order.model.OrderStatus;
import com.multirestaurantplatform.order.service.OrderService;
import com.multirestaurantplatform.security.model.User;
import com.multirestaurantplatform.security.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class OrderHistoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private UserRepository userRepository;

    private static final String USERNAME = "testUser";
    private static final Long USER_ID = 1L;

    @Test
    @WithMockUser(username = USERNAME, roles = {"CUSTOMER"})
    public void testGetOrderHistory() throws Exception {
        // Arrange
        User user = new User();
        user.setId(USER_ID);
        user.setUsername(USERNAME);

        Order order1 = createSampleOrder(1L, USER_ID, OrderStatus.DELIVERED);
        Order order2 = createSampleOrder(2L, USER_ID, OrderStatus.CONFIRMED);
        List<Order> orders = Arrays.asList(order1, order2);

        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(orderService.findOrdersByCustomerId(USER_ID)).thenReturn(orders);

        // Act & Assert
        mockMvc.perform(get("/api/v1/users/{userId}/orders", USERNAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[1].id", is(2)));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = {"CUSTOMER"})
    public void testGetPaginatedOrderHistory() throws Exception {
        // Arrange
        User user = new User();
        user.setId(USER_ID);
        user.setUsername(USERNAME);

        Order order = createSampleOrder(1L, USER_ID, OrderStatus.DELIVERED);
        Page<Order> orderPage = new PageImpl<>(List.of(order));

        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(orderService.findOrdersByCustomerId(eq(USER_ID), any(Pageable.class))).thenReturn(orderPage);

        // Act & Assert
        mockMvc.perform(get("/api/v1/users/{userId}/orders/paged", USERNAME)
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "createdAt")
                        .param("direction", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", is(1)))
                .andExpect(jsonPath("$.content[0].id", is(1)))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = {"CUSTOMER"})
    public void testGetFilteredOrderHistory() throws Exception {
        // Arrange
        User user = new User();
        user.setId(USER_ID);
        user.setUsername(USERNAME);

        Order order = createSampleOrder(1L, USER_ID, OrderStatus.DELIVERED);
        Page<Order> orderPage = new PageImpl<>(List.of(order));

        LocalDateTime startDate = LocalDateTime.now().minusDays(7);
        LocalDateTime endDate = LocalDateTime.now();
        String formattedStartDate = startDate.format(DateTimeFormatter.ISO_DATE_TIME);
        String formattedEndDate = endDate.format(DateTimeFormatter.ISO_DATE_TIME);

        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(orderService.findFilteredOrdersByCustomerId(
                eq(USER_ID), eq(OrderStatus.DELIVERED), any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(orderPage);

        // Act & Assert
        mockMvc.perform(get("/api/v1/users/{userId}/orders/filtered", USERNAME)
                        .param("status", "DELIVERED")
                        .param("startDate", formattedStartDate)
                        .param("endDate", formattedEndDate)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", is(1)))
                .andExpect(jsonPath("$.content[0].status", is("DELIVERED")));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = {"CUSTOMER"})
    public void testGetOrderStatistics() throws Exception {
        // Arrange
        User user = new User();
        user.setId(USER_ID);
        user.setUsername(USERNAME);

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

        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(orderService.getOrderStatisticsForCustomer(USER_ID)).thenReturn(statistics);

        // Act & Assert
        mockMvc.perform(get("/api/v1/users/{userId}/orders/statistics", USERNAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders", is(5)))
                .andExpect(jsonPath("$.totalSpent", is(150.5)))
                .andExpect(jsonPath("$.ordersByStatus.DELIVERED", is(3)))
                .andExpect(jsonPath("$.mostOrderedRestaurantName", is("Test Restaurant")));
    }

    @Test
    @WithMockUser(username = "otherUser", roles = {"CUSTOMER"})
    public void testAccessDenied_WhenAccessingOtherUserOrders() throws Exception {
        // Act & Assert - A customer shouldn't be able to access another customer's orders
        mockMvc.perform(get("/api/v1/users/{userId}/orders", USERNAME))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "adminUser", roles = {"ADMIN"})
    public void testAdmin_CanAccessAnyUserOrders() throws Exception {
        // Arrange
        User user = new User();
        user.setId(USER_ID);
        user.setUsername(USERNAME);

        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(orderService.findOrdersByCustomerId(USER_ID)).thenReturn(List.of());

        // Act & Assert - An admin should be able to access any user's orders
        mockMvc.perform(get("/api/v1/users/{userId}/orders", USERNAME))
                .andExpect(status().isOk());
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