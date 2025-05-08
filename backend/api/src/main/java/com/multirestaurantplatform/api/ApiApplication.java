package com.multirestaurantplatform.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan; // Import needed for @EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories; // Import needed for @EnableJpaRepositories

// 1. Broaden component scanning to include all modules under the base package
@SpringBootApplication(scanBasePackages = "com.multirestaurantplatform")
// 2. Explicitly tell Spring Data JPA where to find repositories
@EnableJpaRepositories(basePackages = "com.multirestaurantplatform.security.repository") // Add other repo packages later if needed, e.g., ", com.multirestaurantplatform.order.repository"
// 3. Explicitly tell JPA where to find entities (optional but good practice in multi-module)
@EntityScan(basePackages = {"com.multirestaurantplatform.security.model", "com.multirestaurantplatform.common.model"}) // Include packages containing @Entity or @MappedSuperclass
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }

}