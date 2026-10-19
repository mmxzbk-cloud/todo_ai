package com.aiassistant.tag;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aiassistant.auth.CurrentUserProvider;
import com.aiassistant.tag.dto.TagRequest;
import com.aiassistant.tag.dto.TagResponse;

import jakarta.validation.Valid;

/**
 * 标签接口。Controller 只做协议转换：取当前用户、触发校验、把结果交给 Service。
 */
@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

	private final TagService service;
	private final CurrentUserProvider currentUserProvider;

	public TagController(TagService service, CurrentUserProvider currentUserProvider) {
		this.service = service;
		this.currentUserProvider = currentUserProvider;
	}

	@GetMapping
	public List<TagResponse> list() {
		return service.list(ownerId());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TagResponse create(@Valid @RequestBody TagRequest request) {
		return service.create(ownerId(), request);
	}

	@PutMapping("/{id}")
	public TagResponse update(@PathVariable Long id, @Valid @RequestBody TagRequest request) {
		return service.update(ownerId(), id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		service.delete(ownerId(), id);
	}

	/** ownerId 只从认证上下文取，绝不从请求体读，否则任何人都能改别人的数据。 */
	private Long ownerId() {
		return currentUserProvider.require().id();
	}
}
