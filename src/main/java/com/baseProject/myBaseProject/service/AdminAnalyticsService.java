package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.admin.AdminAnalyticsResponse;
import com.baseProject.myBaseProject.dto.admin.AdminAnalyticsTimeSeriesResponse;

import java.time.Instant;

public interface AdminAnalyticsService {
    AdminAnalyticsResponse summary(Instant from, Instant to);

    AdminAnalyticsTimeSeriesResponse timeSeries(Instant from, Instant to);

    byte[] exportCsv(Instant from, Instant to);
}
