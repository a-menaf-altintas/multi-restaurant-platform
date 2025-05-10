package com.multirestaurantplatform.api;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.multirestaurantplatform")
@EnableJpaRepositories(basePackages = {
        "com.multirestaurantplatform.security.repository",
        "com.multirestaurantplatform.restaurant.repository" // Add this
})
@EntityScan(basePackages = {
        "com.multirestaurantplatform.security.model",
        "com.multirestaurantplatform.common.model",
        "com.multirestaurantplatform.restaurant.model" // Add this
})
public class ApiApplication {

    public static void main(String[] args) {
        // Configure Dotenv to look for the .env file in the project root directory.
        Dotenv dotenv = Dotenv.configure()
                .directory("../../") // Path from backend/api to project root
                .filename(".env")    // Explicitly specify the filename
                .ignoreIfMissing()   // Don't throw an error if .env is not found
                .ignoreIfMalformed() // Don't throw an error if .env is malformed
                .load();

        String loadedSecret = dotenv.get("JWT_SECRET_KEY");

        if (loadedSecret != null && !loadedSecret.trim().isEmpty()) {
            // Explicitly set the loaded secret as a system property
            // BEFORE SpringApplication.run() to ensure Spring Boot picks it up.
            System.setProperty("JWT_SECRET_KEY", loadedSecret);
        }
        // Removed debug print lines for security

        SpringApplication.run(ApiApplication.class, args);
    }
}
    