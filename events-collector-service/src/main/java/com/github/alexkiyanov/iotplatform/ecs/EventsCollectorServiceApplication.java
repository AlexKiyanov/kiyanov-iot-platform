package com.github.alexkiyanov.iotplatform.ecs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class EventsCollectorServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventsCollectorServiceApplication.class, args);
	}

}
