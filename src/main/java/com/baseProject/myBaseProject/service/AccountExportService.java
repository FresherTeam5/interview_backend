package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.account.AccountExportResponse;

public interface AccountExportService {
    AccountExportResponse export(Long userId);
}
