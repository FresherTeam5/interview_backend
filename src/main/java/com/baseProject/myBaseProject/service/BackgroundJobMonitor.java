package com.baseProject.myBaseProject.service;

import java.util.function.IntSupplier;

public interface BackgroundJobMonitor {
    int runScheduled(String jobName, IntSupplier action);

    int runManual(String jobName, Long adminId, IntSupplier action);
}
