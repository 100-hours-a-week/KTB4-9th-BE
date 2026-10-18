package com.cosmos.cosmos_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CosmosBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(CosmosBackendApplication.class, args);
	}

}
