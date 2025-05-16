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
        "com.multirestaurantplatform.order.repository", // Existing
        "com.multirestaurantplatform.payment.repository" // Added for payment module (good for future use)
})
@EntityScan(basePackages = {
        "com.multirestaurantplatform.security.model",
        "com.multirestaurantplatform.common.model",    // BaseEntity is here
        "com.multirestaurantplatform.restaurant.model",
        "com.multirestaurantplatform.menu.model",
        "com.multirestaurantplatform.order.model",      // Existing
        "com.multirestaurantplatform.payment.model" // Added for payment module (good for future use)
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

        // Load JWT_SECRET_KEY and set as system property
        setSystemPropertyFromDotenv(dotenv, "JWT_SECRET_KEY");

        // Load Stripe keys and set as system properties
        setSystemPropertyFromDotenv(dotenv, "STRIPE_SECRET_KEY");
        setSystemPropertyFromDotenv(dotenv, "STRIPE_PUBLISHABLE_KEY");
        setSystemPropertyFromDotenv(dotenv, "STRIPE_WEBHOOK_SECRET");

        // Ensure other .env variables are similarly propagated if needed as system properties
        // or accessed directly via dotenv instance in specific configurations.

        SpringApplication.run(ApiApplication.class, args);
    }

    /**
     * Helper method to load a variable from Dotenv and set it as a system property.
     * @param dotenv The Dotenv instance
     * @param variableName The name of the environment variable to load
     */
    private static void setSystemPropertyFromDotenv(Dotenv dotenv, String variableName) {
        String value = dotenv.get(variableName);
        if (value != null && !value.trim().isEmpty()) {
            System.setProperty(variableName, value);
            // For debugging:
            // System.out.println("Loaded " + variableName + " from .env and set as system property.");
        } else {
            // For debugging:
            // System.out.println(variableName + " not found in .env or is empty. It might be set directly in the OS environment.");
        }
    }
}