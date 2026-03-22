package com.bootlabs.logging.config;

import com.bootlabs.logging.interceptor.OutgoingHttpLoggingInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Provides a {@link RestClient} bean pre-wired with
 * {@link OutgoingHttpLoggingInterceptor}.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @Service
 * public class BookApiClient {
 *
 *     private final RestClient restClient;
 *
 *     public BookApiClient(RestClient restClient) {
 *         this.restClient = restClient;
 *     }
 *
 *     public BookDto fetchBook(long id) {
 *         return restClient.get()
 *                 .uri("https://api.example.com/books/{id}", id)
 *                 .retrieve()
 *                 .body(BookDto.class);
 *     }
 * }
 * }</pre>
 *
 * <p>If you need multiple RestClients with different base URLs, inject
 * {@link RestClient.Builder} and add the interceptor yourself:
 * <pre>{@code
 * RestClient.builder()
 *     .baseUrl("https://api.example.com")
 *     .requestInterceptor(loggingInterceptor)
 *     .build();
 * }</pre>
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient(OutgoingHttpLoggingInterceptor loggingInterceptor) {
        return RestClient.builder()
                .requestInterceptor(loggingInterceptor)
                .build();
    }
}
