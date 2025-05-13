package com.multirestaurantplatform.order.config;

import com.multirestaurantplatform.order.repository.CartItemRepository;
import com.multirestaurantplatform.order.repository.CartRepository;
import com.multirestaurantplatform.order.service.CartService;
import com.multirestaurantplatform.order.service.InMemoryCartServiceImpl;
import com.multirestaurantplatform.order.service.client.MenuServiceClient;
import com.multirestaurantplatform.order.service.impl.PersistentCartServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for Cart Service implementation.
 * This configuration provides a single CartService implementation
 * based on the app.cart.storage property (default: 'db').
 */
@Configuration
public class CartServiceConfig {

    private static final Logger logger = LoggerFactory.getLogger(CartServiceConfig.class);

    /**
     * Configure which cart storage to use: 'memory' or 'db' (default)
     */
    @Value("${app.cart.storage:db}")
    private String cartStorageType;

    /**
     * Provides the CartService implementation based on configuration.
     *
     * @param menuServiceClient Client for retrieving menu item details
     * @param cartRepository Repository for cart entities
     * @param cartItemRepository Repository for cart item entities
     * @return The configured CartService implementation
     */
    @Bean
    @Primary
    public CartService cartService(
            MenuServiceClient menuServiceClient,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository) {

        if ("memory".equalsIgnoreCase(cartStorageType)) {
            logger.info("Using in-memory cart storage implementation");
            return new InMemoryCartServiceImpl(menuServiceClient);
        } else {
            logger.info("Using database-persistent cart storage implementation");
            return new PersistentCartServiceImpl(cartRepository, cartItemRepository, menuServiceClient);
        }
    }
}