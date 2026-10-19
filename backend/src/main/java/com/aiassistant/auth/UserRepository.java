package com.aiassistant.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 账号仓储。
 *
 * <p>查询方法的取名不是随手写的：带 {@code AndDeletedAtIsNull} 的是「业务上可见的账号」，
 * 不带的是「这个标识被占用了没有」。两组不能混用——
 * 登录用前者（注销的账号登不进来），查重用后者（用户名不回收，与数据库唯一索引的口径一致）。
 */
public interface UserRepository extends JpaRepository<User, Long> {

	/** 认证过滤器按令牌里的 sub 回表：查不到（或已注销）就当令牌无效。 */
	Optional<User> findByIdAndDeletedAtIsNull(Long id);

	/** 登录用。 */
	Optional<User> findByUsernameAndDeletedAtIsNull(String username);

	/** 注册查重。口径与 uq_users_username 一致：已软删的行同样占用这个名字。 */
	boolean existsByUsername(String username);

	/** 注册查重。口径与 uq_users_email 一致。调用方要先确认邮箱非空，不要传 null 进来。 */
	boolean existsByEmail(String email);

	/** 改邮箱时的查重（排除自己）。本轮还没有改邮箱的入口，为后续的个人设置留着。 */
	boolean existsByEmailAndIdNot(String email, Long id);
}
