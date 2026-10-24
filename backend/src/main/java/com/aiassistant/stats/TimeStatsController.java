package com.aiassistant.stats;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aiassistant.auth.CurrentUser;
import com.aiassistant.auth.CurrentUserProvider;
import com.aiassistant.stats.dto.TimeStatsResponse;

/**
 * 耗时统计接口（US-05）。只读，不写任何数据。
 *
 * <p>统计不受 AI 总开关影响（PRD US-05 开关行为）：它不调用 AI 服务，
 * 门禁与 AI 能力包无关，开关关闭时照常可用。
 */
@RestController
@RequestMapping("/api/v1/stats")
public class TimeStatsController {

	private final TimeStatsService service;
	private final CurrentUserProvider currentUserProvider;

	public TimeStatsController(TimeStatsService service, CurrentUserProvider currentUserProvider) {
		this.service = service;
		this.currentUserProvider = currentUserProvider;
	}

	/**
	 * 「今天」与「本周」的边界按用户的时区算（users.timezone），
	 * 与今日视图同一口径，服务器时区不影响统计结果。
	 */
	@GetMapping("/time")
	public TimeStatsResponse time(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		CurrentUser user = currentUserProvider.require();
		return service.stats(user.id(), user.zoneId(), from, to);
	}
}
