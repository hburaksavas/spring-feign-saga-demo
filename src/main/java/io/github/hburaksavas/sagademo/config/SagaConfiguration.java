package io.github.hburaksavas.sagademo.config;

import feign.Client;
import io.github.hburaksavas.sagademo.saga.SagaFeignClient;
import io.github.hburaksavas.sagademo.saga.SagaProperties;
import io.github.hburaksavas.sagademo.saga.SagaStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SagaProperties.class)
public class SagaConfiguration {

    @Bean
    Client feignClient(SagaStore sagaStore, SagaProperties properties) {
        return new SagaFeignClient(new Client.Default(null, null), sagaStore, properties);
    }

    @Bean
    RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }
}
