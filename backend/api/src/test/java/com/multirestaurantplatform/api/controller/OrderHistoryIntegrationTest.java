package com.multirestaurantplatform.api.controller;

import com.multirestaurantplatform.order.dto.OrderResponse;
import com.multirestaurantplatform.order.model.Order;
import com.multirestaurantplatform.order.service.OrderService;
import com.multirestaurantplatform.security.model.User;
import com.multirestaurantplatform.security.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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

    @Test
    @WithMockUser(username = "customer", roles = {"CUSTOMER"})
    public void testGetOrderHistory_AsCustomer() throws Exception {
        // Arrange
        String username = "customer";
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
        
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(orderService.findOrdersByCustomerId(userId)).thenReturn(orderList);
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/users/{userId}/orders", username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void testGetOrderHistory_AsAdmin() throws Exception {
        // Arrange
        String username = "customer";
        Long userId = 1L;
        
        User user = new User();
        user.setId(userId);
        user.setUsername(username);
        
        Order order1 = new Order();
        order1.setId(1L);
        order1.setCustomerId(userId);
        
        List<Order> orderList = Arrays.asList(order1);
        
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(orderService.findOrdersByCustomerId(userId)).thenReturn(orderList);
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/users/{userId}/orders", username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(username = "otherCustomer", roles = {"CUSTOMER"})
    public void testGetOrderHistory_AsOtherCustomer_ShouldFail() throws Exception {
        // Arrange
        String username = "customer";
        Long userId = 1L;
        
        User user = new User();
        user.setId(userId);
        user.setUsername(username);
        
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/users/{userId}/orders", username))
                .andExpect(status().isForbidden());
    }
}