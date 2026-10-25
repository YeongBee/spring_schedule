package com.sub.schejulde.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record ScheduleUpdateRequest(
        @NotBlank(message = "Cron 표현식(cronExpression)은 필수입니다.")
        @Size(max = 120, message = "Cron 표현식(cronExpression)은 120자 이하여야 합니다.")
        String cronExpression,

        @Size(max = 250, message = "설명(description)은 250자 이하여야 합니다.")
        String description,

        Map<@NotBlank(message = "jobData 의 키는 비어 있을 수 없습니다.") String,
            @NotNull(message = "jobData 의 값은 null 일 수 없습니다.") String> jobData
) {
}
