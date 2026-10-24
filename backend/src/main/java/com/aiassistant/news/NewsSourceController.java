package com.aiassistant.news;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiassistant.auth.CurrentUserProvider;
import com.aiassistant.news.dto.EnabledRequest;
import com.aiassistant.news.dto.SourceRequest;
import com.aiassistant.news.dto.SourceResponse;
import com.aiassistant.news.dto.TrialFetchResponse;

import jakarta.validation.Valid;

/**
 * 来源管理接口（PRD US-01）。/api/v1/news 前缀下全部要求登录（SecurityConfig 默认规则），
 * 数据按 owner 隔离，他人的来源按 404 处理。
 */
@RestController
@RequestMapping("/api/v1/news/sources")
public class NewsSourceController {

	private final NewsSourceService service;
	private final NewsCollectionService collectionService;
	private final CurrentUserProvider currentUserProvider;

	public NewsSourceController(NewsSourceService service, NewsCollectionService collectionService,
			CurrentUserProvider currentUserProvider) {
		this.service = service;
		this.collectionService = collectionService;
		this.currentUserProvider = currentUserProvider;
	}

	@GetMapping
	public List<SourceResponse> list() {
		return service.list(ownerId());
	}

	@PostMapping
	public SourceResponse create(@Valid @RequestBody SourceRequest request) {
		return service.create(ownerId(), request);
	}

	/** 保存前试抓（AC 1.3）：立即抓一次返回预览，不写库。 */
	@PostMapping("/trial")
	public TrialFetchResponse trial(@Valid @RequestBody SourceRequest request) {
		return collectionService.trialFetch(request);
	}

	@PutMapping("/{id}")
	public SourceResponse update(@PathVariable Long id, @Valid @RequestBody SourceRequest request) {
		return service.update(ownerId(), id, request);
	}

	@PatchMapping("/{id}/enabled")
	public SourceResponse setEnabled(@PathVariable Long id, @Valid @RequestBody EnabledRequest request) {
		return service.setEnabled(ownerId(), id, request.enabled());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		service.delete(ownerId(), id);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}

	private Long ownerId() {
		return currentUserProvider.require().id();
	}
}
