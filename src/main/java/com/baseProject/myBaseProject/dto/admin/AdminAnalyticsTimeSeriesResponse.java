package com.baseProject.myBaseProject.dto.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AdminAnalyticsTimeSeriesResponse(
        Instant from,
        Instant to,
        List<Day> days) {

    public record Day(
            LocalDate date,
            long newUsers,
            long sessionsCreated,
            long sessionsCompleted,
            long sessionsFailed,
            long supportTicketsCreated) {
    }
}
