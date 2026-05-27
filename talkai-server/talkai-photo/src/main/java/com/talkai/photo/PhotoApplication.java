package com.talkai.photo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.talkai")
@EnableDiscoveryClient
public class PhotoApplication {
    public static void main(String[] args) {
        SpringApplication.run(PhotoApplication.class, args);
    }
}
