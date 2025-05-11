package com.multirestaurantplatform.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.multirestaurantplatform.order",         // Scan for CartController, services in this module
        "com.multirestaurantplatform.security.config", // To pick up SecurityConfig if @Imported by tests
        "com.multirestaurantplatform.api.exception"  // Scan for GlobalExceptionHandler
})
public class OrderModuleTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderModuleTestApplication.class, args);
    }
}
