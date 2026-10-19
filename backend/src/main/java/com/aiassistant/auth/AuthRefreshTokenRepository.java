package com.aiassistant.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 刷新令牌仓储。
 *
 * <p>只按 hash 查：明文不进查询参数以外的任何地方，也就不存在「按明文建索引」这种误用。
 */
public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshToken, Long> {

	Optional<AuthRefreshToken> findByTokenHash(String tokenHash);
}
