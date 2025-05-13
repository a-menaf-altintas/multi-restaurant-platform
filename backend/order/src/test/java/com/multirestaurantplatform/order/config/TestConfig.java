package com.multirestaurantplatform.order.config;

import com.multirestaurantplatform.order.service.client.MenuServiceClient;
import com.multirestaurantplatform.order.service.client.StubMenuServiceClientImpl;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Test configuration for the order module.
 * This provides necessary beans and configuration for integration tests.
 */
@Configuration
@EnableAutoConfiguration
@EntityScan(basePackages = {
        "com.multirestaurantplatform.order.model", 
        "com.multirestaurantplatform.common.model"
})
@EnableJpaRepositories(basePackages = "com.multirestaurantplatform.order.repository")
@Profile("test")
public class TestConfig {

    @Bean
    public MenuServiceClient menuServiceClient() {
        return new StubMenuServiceClientImpl();
    }
}