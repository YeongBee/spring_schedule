package com.sub.schejulde.dto;

/**
 * jobName / jobGroup 공통 제약.
 * URL 경로(/api/schedules/{jobGroup}/{jobName})로 그대로 쓰이므로 '/', 공백, 한글 등은 허용하지 않는다.
 */
public final class ScheduleConstraints {

    public static final String KEY_PATTERN = "^[A-Za-z0-9_-]+$";
    /** 빈 값 허용 (빈 값이면 DEFAULT 그룹으로 지정) */
    public static final String OPTIONAL_KEY_PATTERN = "^[A-Za-z0-9_-]*$";
    public static final String KEY_PATTERN_MESSAGE = "영문, 숫자, '_', '-' 만 사용할 수 있습니다.";

    private ScheduleConstraints() {
    }
}
