package com.sub.schejulde.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 스케줄 생성 요청.
 * <p>
 * 길이 제한은 Quartz 테이블 컬럼 크기 기준
 * (JOB_NAME/JOB_GROUP VARCHAR2(200), DESCRIPTION VARCHAR2(250), CRON_EXPRESSION VARCHAR2(120)).
 * jobName 은 트리거 이름(jobName + "_trigger")에도 쓰이므로 여유를 두어 100자로 제한한다.
 */
public record ScheduleRequest(
        @NotBlank(message = "작업 이름(jobName)은 필수입니다.")
        @Size(max = 100, message = "작업 이름(jobName)은 100자 이하여야 합니다.")
        @Pattern(regexp = ScheduleConstraints.KEY_PATTERN, message = ScheduleConstraints.KEY_PATTERN_MESSAGE)
        String jobName,

        @Size(max = 100, message = "작업 그룹(jobGroup)은 100자 이하여야 합니다.")
        @Pattern(regexp = ScheduleConstraints.OPTIONAL_KEY_PATTERN, message = ScheduleConstraints.KEY_PATTERN_MESSAGE)
        String jobGroup,

        @NotBlank(message = "Cron 표현식(cronExpression)은 필수입니다.")
        @Size(max = 120, message = "Cron 표현식(cronExpression)은 120자 이하여야 합니다.")
        String cronExpression,

        @Size(max = 250, message = "설명(description)은 250자 이하여야 합니다.")
        String description,

        Map<@NotBlank(message = "jobData 의 키는 비어 있을 수 없습니다.") String,
            @NotNull(message = "jobData 의 값은 null 일 수 없습니다.") String> jobData
) {
    public String resolvedGroup() {
        return (jobGroup == null || jobGroup.isBlank()) ? "DEFAULT" : jobGroup.trim();
    }
}
