package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.admin.AdminUserReferenceResponse;
import com.baseProject.myBaseProject.dto.admin.SystemSettingResponse;
import com.baseProject.myBaseProject.dto.admin.UpdateSystemSettingRequest;
import com.baseProject.myBaseProject.entity.SystemSetting;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.AdminAuditAction;
import com.baseProject.myBaseProject.enums.SystemSettingType;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.repository.SystemSettingRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.AdminAuditService;
import com.baseProject.myBaseProject.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SystemSettingServiceImpl implements SystemSettingService {
    public static final String ANNOUNCEMENTS_ENABLED = "ANNOUNCEMENTS_ENABLED";
    public static final String TEMPLATE_REVIEW_REQUIRED = "TEMPLATE_REVIEW_REQUIRED";
    public static final String ADMIN_BULK_RETRY_LIMIT = "ADMIN_BULK_RETRY_LIMIT";
    public static final String BACKGROUND_JOB_RETENTION_DAYS = "BACKGROUND_JOB_RETENTION_DAYS";

    private final SystemSettingRepository settings;
    private final UserAccountRepository users;
    private final AdminAuditService audit;
    private final Clock clock;

    @Override
    public List<SystemSettingResponse> list() {
        return settings.findAll(Sort.by("key")).stream().map(this::map).toList();
    }

    @Override
    @Transactional
    public SystemSettingResponse update(
            Long adminId, String rawKey, UpdateSystemSettingRequest request) {
        String key = rawKey.strip().toUpperCase(Locale.ROOT);
        SystemSetting setting = settings.findByKeyForUpdate(key)
                .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND,
                        "System setting not found"));
        if (setting.getVersion() != request.expectedVersion()) {
            throw new DomainException(ErrorCode.DATA_CONSTRAINT_VIOLATION,
                    "System setting changed; reload it and try again");
        }
        String value = validate(setting, request.value());
        String previous = setting.getValue();
        setting.setValue(value);
        setting.setUpdatedBy(users.getReferenceById(adminId));
        setting.setUpdatedAt(clock.instant());
        settings.flush();
        audit.record(adminId, AdminAuditAction.SYSTEM_SETTING_CHANGED, "SYSTEM_SETTING",
                key, Map.of("value", previous), Map.of("value", value));
        return map(setting);
    }

    @Override
    public boolean booleanValue(String key, boolean fallback) {
        return settings.findById(key)
                .filter(setting -> setting.getType() == SystemSettingType.BOOLEAN)
                .map(SystemSetting::getValue)
                .map(Boolean::parseBoolean)
                .orElse(fallback);
    }

    @Override
    public int integerValue(String key, int fallback) {
        return settings.findById(key)
                .filter(setting -> setting.getType() == SystemSettingType.INTEGER)
                .map(SystemSetting::getValue)
                .map(value -> parseInteger(value, fallback))
                .orElse(fallback);
    }

    private String validate(SystemSetting setting, String rawValue) {
        String value = rawValue.strip();
        switch (setting.getType()) {
            case BOOLEAN -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    throw new DomainException(ErrorCode.VALIDATION_FAILED,
                            "Boolean setting value must be true or false");
                }
                return value.toLowerCase(Locale.ROOT);
            }
            case INTEGER -> {
                int parsed;
                try {
                    parsed = Integer.parseInt(value);
                } catch (NumberFormatException exception) {
                    throw new DomainException(ErrorCode.VALIDATION_FAILED,
                            "Integer setting value is invalid");
                }
                if (ADMIN_BULK_RETRY_LIMIT.equals(setting.getKey())
                        && (parsed < 1 || parsed > 100)) {
                    throw new DomainException(ErrorCode.VALIDATION_FAILED,
                            "ADMIN_BULK_RETRY_LIMIT must be between 1 and 100");
                }
                if (BACKGROUND_JOB_RETENTION_DAYS.equals(setting.getKey())
                        && (parsed < 7 || parsed > 365)) {
                    throw new DomainException(ErrorCode.VALIDATION_FAILED,
                            "BACKGROUND_JOB_RETENTION_DAYS must be between 7 and 365");
                }
                return String.valueOf(parsed);
            }
            case STRING -> {
                return value;
            }
        }
        throw new DomainException(ErrorCode.VALIDATION_FAILED);
    }

    private int parseInteger(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private SystemSettingResponse map(SystemSetting setting) {
        UserAccount updater = setting.getUpdatedBy();
        AdminUserReferenceResponse updatedBy = updater == null ? null
                : new AdminUserReferenceResponse(
                        updater.getId(), updater.getFullName(), updater.getEmail());
        return new SystemSettingResponse(setting.getKey(), setting.getValue(), setting.getType(),
                setting.getDescription(), setting.getVersion(), updatedBy, setting.getUpdatedAt());
    }
}
