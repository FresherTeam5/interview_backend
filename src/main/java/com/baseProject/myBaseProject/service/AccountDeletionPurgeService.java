package com.baseProject.myBaseProject.service;

import java.time.Instant;

public interface AccountDeletionPurgeService {
    boolean purgeDueRequest(Long requestId, Instant now);
}
