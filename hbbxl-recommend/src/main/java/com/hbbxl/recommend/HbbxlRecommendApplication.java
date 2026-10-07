package com.hbbxl.recommend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = {"com.hbbxl"})
public class HbbxlRecommendApplication {
    public static void main(String[] args) {
        SpringApplication.run(HbbxlRecommendApplication.class, args);
    }
}
