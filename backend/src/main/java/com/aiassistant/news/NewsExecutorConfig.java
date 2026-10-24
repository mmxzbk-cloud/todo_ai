package com.aiassistant.news;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 采集专用线程池。
 *
 * <p>为什么不复用 Spring 默认调度线程：现有调度是单线程的（SchedulingConfig 的说明），
 * 一次全量采集可能跑几分钟，占住它会把计时兜底与耗时对账一起堵死。
 * 也不占用 Web 线程池：采集期间列表/检索接口的 P95 不允许被拖累（PRD 3.2）。
 */
@Configuration
public class NewsExecutorConfig {

	@Bean("newsExecutor")
	public ThreadPoolTaskExecutor newsExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("news-");
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(50);
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(30);
		return executor;
	}
}
