package com.sub.schejulde.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;

@Builder
public record ScheduleResponse(
        String jobName,
        String jobGroup,
        String description,
        String cronExpression,
        String timeZone,
        String triggerState,
        LocalDateTime nextFireTime,
        LocalDateTime previousFireTime,
        Map<String, String> jobData
) {
}
