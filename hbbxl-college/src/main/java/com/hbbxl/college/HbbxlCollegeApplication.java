package com.hbbxl.college;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = {"com.hbbxl"})
public class HbbxlCollegeApplication {
    public static void main(String[] args) {
        SpringApplication.run(HbbxlCollegeApplication.class, args);
    }
}
