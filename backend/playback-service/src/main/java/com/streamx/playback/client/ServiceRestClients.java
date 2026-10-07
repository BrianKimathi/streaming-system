package com.streamx.playback.client;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

final class ServiceRestClients {

    private ServiceRestClients() {
    }

    static RestClient create(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
