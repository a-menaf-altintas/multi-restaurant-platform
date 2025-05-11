package com.multirestaurantplatform.order.service;

import com.multirestaurantplatform.order.dto.AddItemToCartRequest;
import com.multirestaurantplatform.order.dto.CartResponse;
import com.multirestaurantplatform.order.dto.UpdateCartItemRequest;

public interface CartService {
    CartResponse addItemToCart(String userId, AddItemToCartRequest addItemRequest);
    CartResponse getCart(String userId);
    CartResponse updateCartItem(String userId, Long menuItemId, UpdateCartItemRequest updateRequest);
    CartResponse removeCartItem(String userId, Long menuItemId);
    void clearCart(String userId);
}