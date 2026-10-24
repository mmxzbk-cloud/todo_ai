package com.aiassistant.news;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiassistant.auth.CurrentUserProvider;
import com.aiassistant.news.dto.DigestResponse;

/**
 * 每日简报接口（PRD US-05）。POST 表达「生成一次」而非资源创建；
 * AI 总开关关闭时入口由前端隐藏，直接调用返回 AI_DISABLED（403）。
 */
@RestController
@RequestMapping("/api/v1/news")
public class NewsDigestController {

	private final NewsDigestService digestService;
	private final CurrentUserProvider currentUserProvider;

	public NewsDigestController(NewsDigestService digestService, CurrentUserProvider currentUserProvider) {
		this.digestService = digestService;
		this.currentUserProvider = currentUserProvider;
	}

	@PostMapping("/digest")
	public DigestResponse generate() {
		var result = digestService.generate(currentUserProvider.require().id());
		return new DigestResponse(result.itemCount(), result.report());
	}
}
