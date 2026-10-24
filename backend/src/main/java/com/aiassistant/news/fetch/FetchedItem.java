package com.aiassistant.news.fetch;

import java.time.Instant;

/**
 * 抓取器产出的一条候选资讯：还没去重、还没入库。
 * contentText 允许为空（RSS 只有摘要、正文页抓取失败或被 robots 拦下时），条目本身仍然有效。
 */
public record FetchedItem(String title, String url, Instant publishedAt, String contentText) {
}
