package com.sub.schejulde.exception;

/**
 * 이미 동일한 (jobGroup, jobName) 스케줄이 존재할 때 발생 -> 409 Conflict
 */
public class DuplicateScheduleException extends RuntimeException {

    public DuplicateScheduleException(String jobGroup, String jobName) {
        super(String.format("이미 동일한 스케줄이 존재합니다: [%s.%s]", jobGroup, jobName));
    }
}
