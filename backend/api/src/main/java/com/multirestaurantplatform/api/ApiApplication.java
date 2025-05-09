package com.multirestaurantplatform.api;

import io.github.cdimascio.dotenv.Dotenv; // Import the Dotenv class
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.multirestaurantplatform")
@EnableJpaRepositories(basePackages = "com.multirestaurantplatform.security.repository") // Add other repo packages as needed
@EntityScan(basePackages = {"com.multirestaurantplatform.security.model", "com.multirestaurantplatform.common.model"}) // Add other entity packages as needed
public class ApiApplication {

    public static void main(String[] args) {
        // Attempt to load environment variables from a .env file located in the project root directory.
        // This makes variables defined in .env available as system properties.
        // Spring Boot will then pick them up for configuration (e.g., in application.properties).
        Dotenv dotenv = Dotenv.configure()
                .directory("./") // Specifies the project root directory.
                // Assumes the application is run with the project root as the working directory.
                .filename(".env") // Explicitly specify the filename (optional, as .env is default)
                .ignoreIfMissing()      // Prevents an error if the .env file is not found.
                .ignoreIfMalformed()    // Prevents an error if the .env file has syntax issues.
                .load();                 // Loads the variables.

        SpringApplication.run(ApiApplication.class, args);
    }
}
    