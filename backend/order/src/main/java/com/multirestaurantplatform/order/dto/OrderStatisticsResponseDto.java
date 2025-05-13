package com.multirestaurantplatform.order.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatisticsResponseDto {
    private Long totalOrders;
    private BigDecimal totalSpent;
    private Map<String, Long> ordersByStatus;
    private BigDecimal averageOrderAmount;
    private LocalDateTime firstOrderDate;
    private LocalDateTime lastOrderDate;
    private Long restaurantCount; // Number of different restaurants ordered from
    private String mostOrderedRestaurantName;
    private Long mostOrderedRestaurantId;
    private Long mostOrderedRestaurantOrderCount;
}