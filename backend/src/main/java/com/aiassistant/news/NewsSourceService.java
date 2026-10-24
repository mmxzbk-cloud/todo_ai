package com.aiassistant.news;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiassistant.common.BadRequestException;
import com.aiassistant.common.ConflictException;
import com.aiassistant.common.ErrorCode;
import com.aiassistant.common.ResourceNotFoundException;
import com.aiassistant.news.dto.NewsParseRule;
import com.aiassistant.news.dto.SourceRequest;
import com.aiassistant.news.dto.SourceResponse;
import com.aiassistant.news.fetch.RobotsChecker;

import tools.jackson.databind.ObjectMapper;

/**
 * 来源管理的业务规则（PRD US-01）：上限、URL 合法性、robots 检查、解析规则结构。
 *
 * <p>与 TaskService 同一约定：ownerId 由 Controller 从认证上下文取好传进来，
 * 查不到的（他人的/不存在的）一律 404。
 */
@Service
public class NewsSourceService {

	private static final Logger log = LoggerFactory.getLogger(NewsSourceService.class);

	private final NewsSourceRepository repository;
	private final RobotsChecker robotsChecker;
	private final NewsProperties properties;
	private final ObjectMapper objectMapper;

	public NewsSourceService(NewsSourceRepository repository, RobotsChecker robotsChecker,
			NewsProperties properties, ObjectMapper objectMapper) {
		this.repository = repository;
		this.robotsChecker = robotsChecker;
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	public List<SourceResponse> list(Long ownerId) {
		return repository.findByOwnerIdOrderByCreatedAtAscIdAsc(ownerId).stream().map(this::toResponse).toList();
	}

	/**
	 * 新建。robots 检查在保存之前做：禁止抓取的 URL 直接拒绝（AC 1.7），
	 * 不会出现「存进去了但永远抓不了」的来源。
	 */
	@Transactional
	public SourceResponse create(Long ownerId, SourceRequest request) {
		if (repository.countByOwnerId(ownerId) >= properties.getMaxSourcesPerUser()) {
			throw new BadRequestException(ErrorCode.VALIDATION_FAILED,
					"订阅来源已达上限（" + properties.getMaxSourcesPerUser() + " 个）");
		}
		String url = validateUrl(request.url());
		assertRobotsAllowed(url);
		String parseRule = serializeParseRule(request);

		NewsSource source = new NewsSource();
		source.setOwnerId(ownerId);
		applyRequest(source, request, url, parseRule);
		try {
			return toResponse(repository.save(source));
		} catch (DataIntegrityViolationException ex) {
			throw new ConflictException(ErrorCode.NEWS_SOURCE_NAME_TAKEN, "同名来源已存在");
		}
	}

	@Transactional
	public SourceResponse update(Long ownerId, Long id, SourceRequest request) {
		NewsSource source = requireSource(ownerId, id);
		String url = validateUrl(request.url());
		if (!url.equals(source.getUrl())) {
			// 换了地址才重新检查 robots：同地址重复检查一次网络往返，不值得
			assertRobotsAllowed(url);
		}
		String parseRule = serializeParseRule(request);
		applyRequest(source, request, url, parseRule);
		// 编辑配置或保持启用都算「人工修复过」：清掉连续失败标记，
		// 否则一个修好了的来源会永远停在异常态、再也不被采集
		if (!Boolean.FALSE.equals(request.enabled()) && source.isAutoDisabled()) {
			source.resetFailureState();
		}
		try {
			return toResponse(repository.save(source));
		} catch (DataIntegrityViolationException ex) {
			throw new ConflictException(ErrorCode.NEWS_SOURCE_NAME_TAKEN, "同名来源已存在");
		}
	}

	/** 启停（AC 1.1）。重新启用等于「我修好了」，清掉连续失败标记，采集管道才会再碰它。 */
	@Transactional
	public SourceResponse setEnabled(Long ownerId, Long id, boolean enabled) {
		NewsSource source = requireSource(ownerId, id);
		source.setEnabled(enabled);
		if (enabled) {
			source.resetFailureState();
		}
		return toResponse(repository.save(source));
	}

	/**
	 * 删除（AC 1.6）。只删来源行：已抓的资讯靠外键 ON DELETE SET NULL 留下，
	 * 来源名展示走条目上的 source_name 快照。
	 */
	@Transactional
	public void delete(Long ownerId, Long id) {
		NewsSource source = requireSource(ownerId, id);
		repository.delete(source);
	}

	@Transactional(readOnly = true)
	public NewsSource requireSource(Long ownerId, Long id) {
		return repository.findByIdAndOwnerId(id, ownerId)
				.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "来源不存在：id=" + id));
	}

	// ---------------------------------------------------------------- 规则落地（试抓与采集服务复用）

	private void applyRequest(NewsSource source, SourceRequest request, String url, String parseRule) {
		source.setName(request.name().trim());
		source.setType(request.type());
		source.setUrl(url);
		source.setParseRule(parseRule);
		source.setDefaultCategory(request.defaultCategory());
		source.setEnabled(request.enabled() == null || request.enabled());
	}

	/** URL 合法性：仅 http/https 且有 host（AC 1.4）。返回修剪后的原文。 */
	public String validateUrl(String rawUrl) {
		String trimmed = rawUrl == null ? "" : rawUrl.trim();
		URI uri;
		try {
			uri = URI.create(trimmed);
		} catch (IllegalArgumentException ex) {
			throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "来源地址不正确：" + trimmed);
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
		if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null) {
			throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "来源地址仅支持 http/https：" + trimmed);
		}
		return trimmed;
	}

	/** robots 检查（AC 1.7）：禁止抓取的地址在保存与试抓时即拒绝。 */
	public void assertRobotsAllowed(String url) {
		if (!robotsChecker.isAllowed(url)) {
			throw new BadRequestException(ErrorCode.ROBOTS_DISALLOWED, "该来源禁止自动抓取（robots.txt）");
		}
	}

	/** 网页类型必须带结构完整的解析规则；RSS 忽略规则字段。落库存 JSON 字符串。 */
	public String serializeParseRule(SourceRequest request) {
		if (request.type() == NewsSourceType.RSS) {
			return null;
		}
		NewsParseRule rule = request.parseRule();
		if (rule == null || isBlank(rule.item()) || isBlank(rule.title()) || isBlank(rule.link())) {
			throw new BadRequestException(ErrorCode.VALIDATION_FAILED,
					"网页来源必须配置 item / title / link 三个选择器");
		}
		try {
			return objectMapper.writeValueAsString(rule);
		} catch (Exception ex) {
			log.error("解析规则序列化失败", ex);
			throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "解析规则无法序列化");
		}
	}

	SourceResponse toResponse(NewsSource source) {
		NewsParseRule parseRule = null;
		if (source.getParseRule() != null) {
			try {
				parseRule = objectMapper.readValue(source.getParseRule(), NewsParseRule.class);
			} catch (Exception ex) {
				log.warn("解析规则反序列化失败，按空返回：sourceId={}", source.getId());
			}
		}
		return new SourceResponse(source.getId(), source.getName(), source.getType(), source.getUrl(),
				parseRule, source.getDefaultCategory(), source.isEnabled(), source.getLastFetchAt(),
				source.getLastFetchOk(), source.getLastFetchMessage(), source.getConsecutiveFailures(),
				source.isAutoDisabled(), source.getCreatedAt());
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
