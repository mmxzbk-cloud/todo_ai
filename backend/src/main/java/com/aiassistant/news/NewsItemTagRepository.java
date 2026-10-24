package com.aiassistant.news;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 资讯-标签关联仓储。方法名里 Id_ItemId 的下划线是显式路径分隔符，与 TaskTagRepository 同一写法。
 */
public interface NewsItemTagRepository extends JpaRepository<NewsItemTag, NewsItemTagId> {

	@Query("SELECT it.id.tagId FROM NewsItemTag it WHERE it.id.infoItemId = :itemId")
	List<Long> findTagIdsByItemId(@Param("itemId") Long itemId);

	/** 先删后插的整体替换：删除是立即执行的语句，顺序由调用方保证。 */
	@Modifying
	@Query("DELETE FROM NewsItemTag it WHERE it.id.infoItemId = :itemId")
	int deleteByItemId(@Param("itemId") Long itemId);
}
