package com.github.alexkiyanov.iotplatform.dcs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class DeviceCollectorServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeviceCollectorServiceApplication.class, args);
    }

}
