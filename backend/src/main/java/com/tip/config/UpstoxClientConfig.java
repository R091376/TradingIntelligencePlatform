package com.tip.config;

import com.upstox.ApiClient;
import com.upstox.auth.OAuth;
import io.swagger.client.api.HistoryV3Api;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UpstoxClientConfig {

    @Bean
    ApiClient upstoxApiClient(UpstoxProperties upstoxProperties) {
        ApiClient apiClient = com.upstox.Configuration.getDefaultApiClient();
        OAuth oauth = (OAuth) apiClient.getAuthentication("OAUTH2");
        oauth.setAccessToken(upstoxProperties.accessToken());
        com.upstox.Configuration.setDefaultApiClient(apiClient);
        return apiClient;
    }

    @Bean
    HistoryV3Api historyV3Api(ApiClient apiClient) {
        return new HistoryV3Api(apiClient);
    }
}