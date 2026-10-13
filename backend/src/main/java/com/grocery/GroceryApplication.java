package com.grocery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@SpringBootApplication
@EnableMongoAuditing
public class GroceryApplication {

    public static void main(String[] args) {
        SpringApplication.run(GroceryApplication.class, args);
    }
}
