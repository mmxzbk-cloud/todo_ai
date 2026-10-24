package com.aiassistant.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 开关的默认值与 upsert 行为。AC 1.2 / 1.5 的业务层落点：
 * 没有行的用户按「关闭」处理，切换开关不依赖也不修改任何任务数据。
 */
@ExtendWith(MockitoExtension.class)
class UserSettingServiceTest {

	@Mock
	private UserSettingRepository repository;

	private UserSettingService service;

	@BeforeEach
	void setUp() {
		service = new UserSettingService(repository);
	}

	@Test
	void userWithoutRowDefaultsToDisabled() {
		when(repository.findById(1L)).thenReturn(Optional.empty());

		assertThat(service.isAiEnabled(1L)).isFalse();
	}

	@Test
	void firstToggleCreatesRow() {
		when(repository.findById(1L)).thenReturn(Optional.empty());
		when(repository.save(any(UserSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));

		assertThat(service.setAiEnabled(1L, true)).isTrue();

		ArgumentCaptor<UserSetting> captor = ArgumentCaptor.forClass(UserSetting.class);
		verify(repository).save(captor.capture());
		assertThat(captor.getValue().getUserId()).isEqualTo(1L);
		assertThat(captor.getValue().isAiEnabled()).isTrue();
	}

	@Test
	void toggleUpdatesExistingRow() {
		UserSetting existing = new UserSetting();
		existing.setUserId(1L);
		existing.setAiEnabled(true);
		when(repository.findById(1L)).thenReturn(Optional.of(existing));
		when(repository.save(any(UserSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));

		assertThat(service.setAiEnabled(1L, false)).isFalse();
		assertThat(existing.isAiEnabled()).isFalse();
	}

	@Test
	void settingsAreStoredPerUser() {
		when(repository.findById(1L)).thenReturn(Optional.empty());
		when(repository.findById(2L)).thenReturn(Optional.empty());
		when(repository.save(any(UserSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.setAiEnabled(1L, true);

		// 用户 2 的读取仍走自己的行（或默认值），与用户 1 的开关无关
		assertThat(service.isAiEnabled(2L)).isFalse();
	}
}
