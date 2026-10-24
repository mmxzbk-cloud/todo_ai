package com.aiassistant.news;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 资讯-任务关联仓储。只读（关联只在转任务时新增）。
 */
public interface NewsItemTaskRepository extends JpaRepository<NewsItemTask, NewsItemTaskId> {

	@Query("SELECT nt.id.taskId FROM NewsItemTask nt WHERE nt.id.infoItemId = :itemId ORDER BY nt.id.taskId")
	List<Long> findTaskIdsByItemId(@Param("itemId") Long itemId);
}
