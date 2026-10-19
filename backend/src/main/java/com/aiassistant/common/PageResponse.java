package com.aiassistant.common;

import java.util.List;

/**
 * 分页出参的统一形状。
 *
 * <p>{@code page} 与请求参数同义（从 0 开始，与 Spring Data 的 Page 一致），
 * 前端算「还有没有下一页」时不必猜这个基准是从 0 还是从 1 开始。
 */
public record PageResponse<T>(List<T> items, long total, int page, int size) {
}
