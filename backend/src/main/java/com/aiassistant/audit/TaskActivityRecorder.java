package com.aiassistant.audit;

import org.springframework.stereotype.Component;

/**
 * 写审计的唯一入口。
 *
 * 设计文档原方案是用 AOP 织入 Service 方法。这里改成显式调用，理由：
 * 本轮只有 TaskService 与 TimerService 两个写入口，显式调用少一层代理，
 * 断点能直接落在产生这条记录的那一行，排查「这条历史是谁写的」不用再翻切面。
 *
 * 调用方必须在事务内（写入口都在 @Transactional 方法里）：审计与业务数据要么一起成功，要么一起回滚，
 * 出现「任务改了但没留下历史」比没有审计更难排查。
 */
@Component
public class TaskActivityRecorder {

	/** 与 task_activity_logs.old_value / new_value 的列长度一致，超长按列宽截断而不是让写入失败。 */
	private static final int MAX_VALUE_LENGTH = 500;

	/** 与 task_activity_logs.field 的列长度一致。 */
	private static final int MAX_FIELD_LENGTH = 50;

	private final TaskActivityLogRepository repository;

	public TaskActivityRecorder(TaskActivityLogRepository repository) {
		this.repository = repository;
	}

	public void record(Long taskId, Long actorId, TaskAction action, String field, String oldValue, String newValue) {
		TaskActivityLog log = new TaskActivityLog();
		log.setTaskId(taskId);
		log.setActorId(actorId);
		log.setAction(action);
		log.setField(truncate(field, MAX_FIELD_LENGTH));
		log.setOldValue(truncate(oldValue, MAX_VALUE_LENGTH));
		log.setNewValue(truncate(newValue, MAX_VALUE_LENGTH));
		repository.save(log);
	}

	private static String truncate(String value, int maxLength) {
		if (value == null || value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength);
	}
}
