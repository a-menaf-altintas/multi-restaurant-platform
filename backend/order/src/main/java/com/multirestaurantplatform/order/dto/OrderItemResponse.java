package com.multirestaurantplatform.order.dto;

import com.multirestaurantplatform.order.model.OrderItem; // Ensure correct import
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    private Long id;
    private Long menuItemId;
    private String menuItemName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal itemTotalPrice;
    private String selectedOptions;

    public static OrderItemResponse fromEntity(OrderItem orderItem) {
        if (orderItem == null) {
            return null;
        }
        return new OrderItemResponse(
                orderItem.getId(),
                orderItem.getMenuItemId(),
                orderItem.getMenuItemName(),
                orderItem.getQuantity(),
                orderItem.getUnitPrice(),
                orderItem.getItemTotalPrice(),
                orderItem.getSelectedOptions()
        );
    }
}