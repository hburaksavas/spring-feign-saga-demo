package io.github.hburaksavas.sagademo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableFeignClients
@EnableScheduling
@EnableTransactionManagement(order = 100)
@SpringBootApplication
public class SagaDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SagaDemoApplication.class, args);
    }
}
