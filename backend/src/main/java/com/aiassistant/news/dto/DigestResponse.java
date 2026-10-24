package com.aiassistant.news.dto;

/**
 * 简报出参。itemCount=0 时 report 是空态文案（AC 5.2：返回空态文案，不调用 AI）。
 */
public record DigestResponse(int itemCount, String report) {
}
