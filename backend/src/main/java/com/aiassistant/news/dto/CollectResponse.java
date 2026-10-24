package com.aiassistant.news.dto;

/**
 * 手动「立即采集」的受理结果：queued 是本次要跑的来源数。
 * 采集全程异步，完成后看来源健康状态（AC 2.7）。
 */
public record CollectResponse(int queued) {
}
