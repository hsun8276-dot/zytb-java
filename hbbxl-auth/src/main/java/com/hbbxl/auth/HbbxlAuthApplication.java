package com.hbbxl.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = {"com.hbbxl"})
public class HbbxlAuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(HbbxlAuthApplication.class, args);
    }
}
