package com.rensights.config;

import java.time.Duration;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Provides a single shared, timeout-bounded {@link RestTemplate} bean.
 *
 * <p>Previously callers created {@code new RestTemplate()} per request with no
 * connect/read timeouts, which allowed a slow or hung upstream to hold a request
 * thread (and its connection/memory) indefinitely. This shared bean applies
 * explicit timeouts so a stalled upstream fails fast with a
 * {@code ResourceAccessException} instead of hanging.
 *
 * <p>Uses {@link SimpleClientHttpRequestFactory} to avoid adding a new HTTP
 * client dependency (Apache HttpClient 5 is not on the classpath).
 */
@Configuration
public class RestClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .requestFactory(SimpleClientHttpRequestFactory::new)
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setReadTimeout(READ_TIMEOUT)
                .build();
    }
}
