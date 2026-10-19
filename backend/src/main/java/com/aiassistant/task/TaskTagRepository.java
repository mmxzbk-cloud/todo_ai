package com.aiassistant.task;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 任务-标签关联仓储。
 *
 * 方法名里的下划线是 Spring Data 显式的路径分隔符：IdTaskId 也能解析成 id.taskId，
 * 但有歧义（分子段有多种切法），写成 Id_TaskId 就不依赖解析规则了。
 */
public interface TaskTagRepository extends JpaRepository<TaskTag, TaskTagId> {

	/**
	 * 只取标签 id，不把关联实体装进持久化上下文。
	 * assignTags 是先删后插同一批主键，若上下文里还留着旧的关联实体，
	 * 后面的插入会被当成对已删除行的更新而报乐观锁失败。
	 */
	@Query("SELECT tt.id.tagId FROM TaskTag tt WHERE tt.id.taskId = :taskId")
	List<Long> findTagIdsByTaskId(@Param("taskId") Long taskId);

	/**
	 * 批量删除是立即执行的语句，所以「先删后插」的顺序由调用顺序保证。
	 * 不加 clearAutomatically：那会把调用方事务里已加载的任务实体也一起清掉。
	 */
	@Modifying
	@Query("DELETE FROM TaskTag tt WHERE tt.id.taskId = :taskId")
	int deleteByTaskId(@Param("taskId") Long taskId);
}
