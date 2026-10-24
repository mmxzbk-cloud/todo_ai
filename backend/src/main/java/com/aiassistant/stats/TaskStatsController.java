package com.aiassistant.stats;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aiassistant.auth.CurrentUser;
import com.aiassistant.auth.CurrentUserProvider;
import com.aiassistant.stats.dto.TaskStatsResponse;

/**
 * 任务概览统计接口。只读，不写任何数据，不依赖 AI 能力（与耗时统计同一条约定）。
 */
@RestController
@RequestMapping("/api/v1/stats")
public class TaskStatsController {

	private final TaskStatsService service;
	private final CurrentUserProvider currentUserProvider;

	public TaskStatsController(TaskStatsService service, CurrentUserProvider currentUserProvider) {
		this.service = service;
		this.currentUserProvider = currentUserProvider;
	}

	/**
	 * 「区间」用天数而不是 from/to：概览统计的起点永远是「今天往前 N 天」，
	 * 让调用方自己拼日期只会多一种拼错的方式；「今天」按用户的时区算。
	 */
	@GetMapping("/tasks")
	public TaskStatsResponse tasks(@RequestParam(defaultValue = "30") int days) {
		CurrentUser user = currentUserProvider.require();
		return service.stats(user.id(), user.zoneId(), days);
	}
}
