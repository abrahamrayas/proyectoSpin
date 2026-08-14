package com.arayas.transaction.infrastructure.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ProviderProperties.class)
public class ProviderClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

	@Bean
	@Qualifier("transaction-provider")
	RestClient transactionProviderRestClient(RestClient.Builder builder, ProviderProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
		requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));

		return builder
				.baseUrl(properties.baseUrl())
				.requestFactory(requestFactory)
				.build();
	}

}
