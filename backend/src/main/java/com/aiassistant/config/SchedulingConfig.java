package com.aiassistant.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务的开关。
 *
 * <p>为什么不挂在启动类上：启动类只负责「这是个 Spring Boot 应用」，
 * 调度、安全、审计这些能力各自在自己的配置类里打开，需要找「谁开的定时任务」时只要看 config 包。
 *
 * <p>为什么不配线程池：本轮只有两条任务（每 10 分钟的计时兜底截断、每日 03:30 的对账），
 * 都是分钟级的短任务，默认的单线程调度器足够；配了线程池反而多一处要解释的并行度设置。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
