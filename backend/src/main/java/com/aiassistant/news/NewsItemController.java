package com.aiassistant.news;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aiassistant.auth.CurrentUserProvider;
import com.aiassistant.common.PageResponse;
import com.aiassistant.news.dto.AdoptRequest;
import com.aiassistant.news.dto.ClassificationRequest;
import com.aiassistant.news.dto.ItemDetailResponse;
import com.aiassistant.news.dto.ItemResponse;
import com.aiassistant.news.dto.ReadRequest;
import com.aiassistant.news.dto.StarRequest;

import jakarta.validation.Valid;

/**
 * 资讯库接口（PRD US-03 / US-04 的展示与采纳）。
 * 列表筛选可组合（分类/来源/日期/星标/未读/关键词），日期按用户时区归界。
 */
@RestController
@RequestMapping("/api/v1/news/items")
public class NewsItemController {

	private final NewsItemService service;
	private final NewsAiService aiService;
	private final CurrentUserProvider currentUserProvider;

	public NewsItemController(NewsItemService service, NewsAiService aiService,
			CurrentUserProvider currentUserProvider) {
		this.service = service;
		this.aiService = aiService;
		this.currentUserProvider = currentUserProvider;
	}

	@GetMapping
	public PageResponse<ItemResponse> list(
			@RequestParam(required = false) NewsCategory category,
			@RequestParam(required = false) Long sourceId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "false") boolean starred,
			@RequestParam(defaultValue = "false") boolean unread,
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.list(ownerId(), category, sourceId, from, to, starred, unread, q, page, size,
				currentUserProvider.require().zoneId());
	}

	@GetMapping("/{id}")
	public ItemDetailResponse detail(@PathVariable Long id) {
		return service.detail(ownerId(), id);
	}

	@PatchMapping("/{id}/read")
	public ItemResponse setRead(@PathVariable Long id, @Valid @RequestBody ReadRequest request) {
		return service.setRead(ownerId(), id, request.read());
	}

	@PatchMapping("/{id}/star")
	public ItemResponse setStarred(@PathVariable Long id, @Valid @RequestBody StarRequest request) {
		return service.setStarred(ownerId(), id, request.starred());
	}

	@PutMapping("/{id}/classification")
	public ItemDetailResponse updateClassification(@PathVariable Long id,
			@Valid @RequestBody ClassificationRequest request) {
		return service.updateClassification(ownerId(), id, request);
	}

	/** 手动重新生成 AI 摘要与建议（AC 4.6）：同步执行，开关关闭返回 AI_DISABLED。 */
	@PostMapping("/{id}/ai/regenerate")
	public ItemDetailResponse regenerate(@PathVariable Long id) {
		aiService.regenerate(ownerId(), id);
		return service.detail(ownerId(), id);
	}

	/** 采纳 AI 建议的分类/标签（AC 4.3）：建议标签名自动解析/建成本人标签。 */
	@PostMapping("/{id}/ai/adopt")
	public ItemDetailResponse adopt(@PathVariable Long id, @Valid @RequestBody AdoptRequest request) {
		return service.adoptAiSuggestions(ownerId(), id, request);
	}

	private Long ownerId() {
		return currentUserProvider.require().id();
	}
}
