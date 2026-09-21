package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.admin.UpdateSystemSettingRequest;
import com.baseProject.myBaseProject.entity.SystemSetting;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.SystemSettingType;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.repository.SystemSettingRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.AdminAuditService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemSettingServiceImplTest {
    @Test
    void bulkRetryLimitCannotExceedRequestValidationCap() {
        SystemSettingRepository settings = mock(SystemSettingRepository.class);
        SystemSetting setting = new SystemSetting();
        setting.setKey(SystemSettingServiceImpl.ADMIN_BULK_RETRY_LIMIT);
        setting.setValue("25");
        setting.setType(SystemSettingType.INTEGER);
        setting.setDescription("limit");
        setting.setUpdatedAt(Instant.parse("2026-09-18T07:00:00Z"));
        when(settings.findByKeyForUpdate(SystemSettingServiceImpl.ADMIN_BULK_RETRY_LIMIT))
                .thenReturn(Optional.of(setting));

        SystemSettingServiceImpl service = new SystemSettingServiceImpl(
                settings, mock(UserAccountRepository.class), mock(AdminAuditService.class),
                Clock.fixed(Instant.parse("2026-09-18T08:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.update(3L,
                SystemSettingServiceImpl.ADMIN_BULK_RETRY_LIMIT,
                new UpdateSystemSettingRequest("101", 0)))
                .isInstanceOf(DomainException.class);
    }
}
