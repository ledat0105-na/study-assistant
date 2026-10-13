package com.example.studyassistant.domain.presentation.generation;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Bounded async executor for presentation generation tasks.
 *
 * presentationTaskExecutor:
 *   - corePoolSize = 2
 *   - maxPoolSize = 4
 *   - queueCapacity = 50
 *   - threadNamePrefix = "pres-gen-"
 *   - RejectedExecutionPolicy = CallerRunsPolicy (degrades gracefully instead of dropping)
 *
 * No commonPool. No unbounded executors. No ad-hoc Thread creation.
 * Only @Async("presentationTaskExecutor") beans should submit to this executor.
 */
@Configuration
@EnableAsync
public class PresentationGenerationConfig {

    @Bean(name = "documentTaskExecutor")
    public ThreadPoolTaskExecutor documentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("doc-index-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean(name = "presentationTaskExecutor")
    public ThreadPoolTaskExecutor presentationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("pres-gen-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
