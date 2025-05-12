// File: backend/api/src/main/java/com/multirestaurantplatform/api/ApiApplication.java
package com.multirestaurantplatform.api;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// scanBasePackages in @SpringBootApplication should cover all your modules' services, components, etc.
// The value "com.multirestaurantplatform" is broad enough to cover all your sub-modules.
@SpringBootApplication(scanBasePackages = "com.multirestaurantplatform")
@EnableJpaRepositories(basePackages = {
        "com.multirestaurantplatform.security.repository",
        "com.multirestaurantplatform.restaurant.repository",
        "com.multirestaurantplatform.menu.repository",
        "com.multirestaurantplatform.order.repository" // Added OrderRepository package
})
@EntityScan(basePackages = {
        "com.multirestaurantplatform.security.model",
        "com.multirestaurantplatform.common.model",    // BaseEntity is here
        "com.multirestaurantplatform.restaurant.model",
        "com.multirestaurantplatform.menu.model",
        "com.multirestaurantplatform.order.model"      // Added Order and OrderItem entity package
})
public class ApiApplication {

    public static void main(String[] args) {
        // Load environment variables from .env file, primarily for local development
        Dotenv dotenv = Dotenv.configure()
                .directory("../../") // Path from backend/api to project root where .env might be
                .filename(".env")    // Explicitly specify the filename
                .ignoreIfMissing()   // Don't throw an error if .env is not found
                .ignoreIfMalformed() // Don't throw an error if .env is malformed
                .load();

        // Example: Set JWT_SECRET_KEY as a system property if found in .env
        // This allows @Value("${JWT_SECRET_KEY}") to pick it up.
        String loadedSecret = dotenv.get("JWT_SECRET_KEY");
        if (loadedSecret != null && !loadedSecret.trim().isEmpty()) {
            System.setProperty("JWT_SECRET_KEY", loadedSecret);
            // For debugging: System.out.println("Loaded JWT_SECRET_KEY from .env and set as system property.");
        } else {
            // For debugging: System.out.println("JWT_SECRET_KEY not found in .env or is empty.");
        }

        // Ensure other .env variables are similarly propagated if needed as system properties
        // or accessed directly via dotenv instance in specific configurations.

        SpringApplication.run(ApiApplication.class, args);
    }
}
