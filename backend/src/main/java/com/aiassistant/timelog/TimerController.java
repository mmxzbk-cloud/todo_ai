package com.aiassistant.timelog;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aiassistant.audit.dto.ActivityResponse;
import com.aiassistant.auth.CurrentUserProvider;
import com.aiassistant.timelog.dto.ManualTimeLogRequest;
import com.aiassistant.timelog.dto.RunningTimerResponse;
import com.aiassistant.timelog.dto.TimeLogResponse;
import com.aiassistant.timelog.dto.TimerStopResponse;

import jakarta.validation.Valid;

/**
 * 计时、手工补录耗时与变更历史。这几个端点都挂在任务下，所以与 task 包的 TaskController
 * 共用 /api/v1/tasks 前缀——Spring 允许同一前缀由多个 controller 提供，两边的完整路径不重叠。
 *
 * <p>owner 一律取自 CurrentUserProvider，不接受请求体或查询参数里的用户标识。
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class TimerController {

	private final TimerService timerService;
	private final CurrentUserProvider currentUserProvider;

	public TimerController(TimerService timerService, CurrentUserProvider currentUserProvider) {
		this.timerService = timerService;
		this.currentUserProvider = currentUserProvider;
	}

	@PostMapping("/{id}/timers")
	@ResponseStatus(HttpStatus.CREATED)
	public TimeLogResponse startTimer(@PathVariable Long id) {
		return timerService.start(id, currentUserProvider.require().id());
	}

	/** markDone=true 时，结束计时的同一个事务里把任务置为已完成。 */
	@PostMapping("/{id}/timers/{logId}/stop")
	public TimerStopResponse stopTimer(@PathVariable Long id, @PathVariable Long logId,
			@RequestParam(name = "markDone", defaultValue = "false") boolean markDone) {
		return timerService.stop(id, logId, markDone, currentUserProvider.require().id());
	}

	@GetMapping("/{id}/time-logs")
	public List<TimeLogResponse> listTimeLogs(@PathVariable Long id) {
		return timerService.listTimeLogs(id, currentUserProvider.require().id());
	}

	@PostMapping("/{id}/time-logs")
	@ResponseStatus(HttpStatus.CREATED)
	public TimeLogResponse addTimeLog(@PathVariable Long id, @Valid @RequestBody ManualTimeLogRequest request) {
		return timerService.addManualLog(id, request, currentUserProvider.require().id());
	}

	@GetMapping("/{id}/activities")
	public List<ActivityResponse> listActivities(@PathVariable Long id) {
		return timerService.listActivities(id, currentUserProvider.require().id());
	}
}

/**
 * 「现在有没有计时在跑」。它不属于某一个任务（前端启动时就要问一次，用来决定是否显示计时条），
 * 路径落在 /api/v1/timers 下。
 *
 * <p>为什么和 TimerController 放在同一个文件里：Spring 会把类上的 @RequestMapping 与方法上的
 * 路径拼接，所以基路径写成 /api/v1/tasks 的类里写不出 /api/v1/timers/running 这个完整路径，
 * 必须是一个基路径为 /api/v1/timers 的独立 controller。它只有一行映射、一个依赖，
 * 单独占一个文件不如和计时接口放在一起读。
 */
@RestController
@RequestMapping("/api/v1/timers")
class RunningTimerController {

	private final TimerService timerService;
	private final CurrentUserProvider currentUserProvider;

	RunningTimerController(TimerService timerService, CurrentUserProvider currentUserProvider) {
		this.timerService = timerService;
		this.currentUserProvider = currentUserProvider;
	}

	/** 没有未结束的计时回 204：前端只看状态码判断，不需要再解析一个空对象。 */
	@GetMapping("/running")
	public ResponseEntity<RunningTimerResponse> running() {
		Long ownerId = currentUserProvider.require().id();
		return timerService.findRunning(ownerId)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.noContent().<RunningTimerResponse>build());
	}
}
