package com.streamx.media.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(MediaProperties.class)
public class AsyncConfig {

    // Transcoding is CPU-heavy; a single worker keeps the host responsive and queues the rest.
    @Bean(name = "transcodeExecutor")
    public Executor transcodeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10_000);
        executor.setThreadNamePrefix("transcode-");
        executor.initialize();
        return executor;
    }

    // Link downloads and server-side assembly of uploaded parts are I/O bound and must not wait behind ffmpeg.
    @Bean(name = "ingestExecutor")
    public Executor ingestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10_000);
        executor.setThreadNamePrefix("ingest-");
        executor.initialize();
        return executor;
    }
}
