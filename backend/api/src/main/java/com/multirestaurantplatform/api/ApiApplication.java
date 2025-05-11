package com.multirestaurantplatform.api;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// Key change: Added scanBasePackages to @SpringBootApplication
@SpringBootApplication(scanBasePackages = "com.multirestaurantplatform")
@EnableJpaRepositories(basePackages = {
        "com.multirestaurantplatform.security.repository",
        "com.multirestaurantplatform.restaurant.repository",
        "com.multirestaurantplatform.menu.repository"
})
@EntityScan(basePackages = {
        "com.multirestaurantplatform.security.model",
        "com.multirestaurantplatform.common.model",
        "com.multirestaurantplatform.restaurant.model",
        "com.multirestaurantplatform.menu.model"
})
public class ApiApplication {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure()
                .directory("../../") // Path from backend/api to project root
                .filename(".env")    // Explicitly specify the filename
                .ignoreIfMissing()   // Don't throw an error if .env is not found
                .ignoreIfMalformed() // Don't throw an error if .env is malformed
                .load();

        String loadedSecret = dotenv.get("JWT_SECRET_KEY");
        if (loadedSecret != null && !loadedSecret.trim().isEmpty()) {
            System.setProperty("JWT_SECRET_KEY", loadedSecret);
        }
        SpringApplication.run(ApiApplication.class, args);
    }
}