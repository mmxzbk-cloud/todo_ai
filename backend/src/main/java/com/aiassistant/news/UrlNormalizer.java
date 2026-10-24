package com.aiassistant.news;

import java.net.URI;

/**
 * 链接归一化（去重判据，PRD US-02 规则 2 / 3.3）。
 *
 * <p>只做「同一篇文章的不同写法」的最小收敛：小写 scheme 与 host、去掉默认端口、
 * 去掉 fragment、空路径补根。刻意不做的事：合并 tracking 参数（规则因站而异，
 * 归一化激进过头会把真不同的文章判成同一条）；去掉末尾斜杠以外的路径改写。
 */
public final class UrlNormalizer {

	private UrlNormalizer() {
	}

	/**
	 * 归一化失败（解析不了）时原样返回修剪后的输入：
	 * 去重是尽力而为的收敛，不该因为一条怪链接挡住整批入库。
	 */
	public static String normalize(String rawUrl) {
		String trimmed = rawUrl == null ? "" : rawUrl.trim();
		URI uri;
		try {
			uri = URI.create(trimmed);
		} catch (IllegalArgumentException ex) {
			return trimmed;
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
		String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
		if (scheme.isEmpty() || host.isEmpty()) {
			return trimmed;
		}
		int port = uri.getPort();
		if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
			port = -1;
		}
		String path = uri.getRawPath();
		if (path == null || path.isEmpty()) {
			path = "/";
		}
		try {
			// fragment 不参与重建：#anchor 后面的部分不影响「是哪篇文章」
			return new URI(scheme, null, host, port, path, uri.getRawQuery(), null).toString();
		} catch (Exception ex) {
			return trimmed;
		}
	}
}
