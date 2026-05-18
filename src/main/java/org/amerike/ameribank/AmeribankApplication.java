package org.amerike.ameribank;

import org.amerike.ameribank.config.security;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AmeribankApplication {
    public static void main(String[] args) {
        security.init();
        SpringApplication.run(AmeribankApplication.class, args);
    }
}
