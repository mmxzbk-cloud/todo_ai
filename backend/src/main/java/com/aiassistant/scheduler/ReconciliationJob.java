package com.aiassistant.scheduler;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.aiassistant.task.Task;
import com.aiassistant.task.TaskRepository;
import com.aiassistant.timelog.TaskTimeLogRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 冗余字段对账：用 task_time_logs 明细重算每个任务的 tasks.actual_minutes，不一致就按明细修正。
 *
 * <p>设计文档 2.7 的要求是「没有对账，冗余字段迟早会和明细对不上，而且没人会发现」：
 * 所以这里不只修，还要 log.warn 把对不上的差额留痕，否则修完了也不知道系统出过问题。
 *
 * <p>每日 03:30 跑，避开使用时段。多实例部署时每个实例都会跑一遍——本项目是 H2 单实例，
 * 不存在这个问题；将来换库要记得加分布式锁。
 */
@Component
public class ReconciliationJob {

	private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

	/**
	 * 把累计列直接写成明细的合计值。
	 *
	 * <p>不用 Task.addActualMinutes 的增量写法：那个入口会忽略负数，一旦累计值比明细大
	 * （多算过一次、明细被清理过等），差值永远修不回来，而对账的意义就是「回到一致」。
	 * updated_at 必须显式写：批量 UPDATE 不触发 @PreUpdate。
	 */
	private static final String CORRECT_ACTUAL_MINUTES = """
			UPDATE Task t SET t.actualMinutes = :minutes, t.updatedAt = :now WHERE t.id = :taskId
			""";

	/** 明细里没有这个任务时的合计：任务是刚建的、或者还没有任何耗时记录。 */
	private static final long NO_MINUTES_LOGGED = 0L;

	private final TaskTimeLogRepository timeLogRepository;
	private final TaskRepository taskRepository;

	/** 事务边界与错误边界分开：见 TimerSweeper 里同样的说明。 */
	private final TransactionTemplate transactionTemplate;

	@PersistenceContext
	private EntityManager entityManager;

	public ReconciliationJob(TaskTimeLogRepository timeLogRepository, TaskRepository taskRepository,
			PlatformTransactionManager transactionManager) {
		this.timeLogRepository = timeLogRepository;
		this.taskRepository = taskRepository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	/** 加一层事务：整批要么按明细修完，要么一条都不动，不留「修了一半」的中间态。 */
	@Scheduled(cron = "0 30 3 * * *")
	public void reconcileActualMinutes() {
		int fixed;
		try {
			fixed = transactionTemplate.execute(status -> reconcile());
		} catch (RuntimeException ex) {
			// 对账失败不致命：下一个周期还会重来。但要留下错误日志，不能静默跳过
			log.error("实际耗时对账失败，本轮不做任何修正（累计列仍与流水不一致）", ex);
			return;
		}
		if (fixed > 0) {
			log.warn("实际耗时对账结束：{} 个任务的累计值与流水不一致，已按流水修正", fixed);
		}
	}

	private int reconcile() {
		Map<Long, Long> minutesByTask = sumMinutesByTask();
		Instant now = Instant.now();
		int fixed = 0;
		// 只对账未删任务：软删的任务不再展示，为它维护累计值没有意义
		for (Task task : taskRepository.findAll()) {
			if (task.getDeletedAt() != null) {
				continue;
			}
			long fromLogs = minutesByTask.getOrDefault(task.getId(), NO_MINUTES_LOGGED);
			int recorded = task.getActualMinutes();
			if (recorded == fromLogs) {
				continue;
			}
			log.warn("实际耗时与流水对不上，按流水修正：taskId={}, tasks.actual_minutes={}, SUM(task_time_logs.minutes)={}",
					task.getId(), recorded, fromLogs);
			// 用一条批量 UPDATE 直接写目标值，不用实体的增量入口：增量入口会忽略负数，修不回多算的值。
			// 注意批量 UPDATE 绕过持久化上下文，所以这里只读实体、不改实体，避免提交时把旧值刷回去。
			entityManager.createQuery(CORRECT_ACTUAL_MINUTES)
					.setParameter("minutes", (int) Math.min(fromLogs, Integer.MAX_VALUE))
					.setParameter("now", now)
					.setParameter("taskId", task.getId())
					.executeUpdate();
			fixed++;
		}
		return fixed;
	}

	private Map<Long, Long> sumMinutesByTask() {
		List<Object[]> rows = timeLogRepository.sumMinutesGroupedByTask();
		Map<Long, Long> minutesByTask = new HashMap<>(rows.size());
		for (Object[] row : rows) {
			Long taskId = (Long) row[0];
			// SUM 在 H2 上回来的可能是 Long，也可能是别的 Number 实现，不假设具体类型
			long minutes = ((Number) row[1]).longValue();
			minutesByTask.put(taskId, minutes);
		}
		return minutesByTask;
	}
}
