package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.admin.SystemSettingResponse;
import com.baseProject.myBaseProject.dto.admin.UpdateSystemSettingRequest;

import java.util.List;

public interface SystemSettingService {
    List<SystemSettingResponse> list();

    SystemSettingResponse update(
            Long adminId, String key, UpdateSystemSettingRequest request);

    boolean booleanValue(String key, boolean fallback);

    int integerValue(String key, int fallback);
}
