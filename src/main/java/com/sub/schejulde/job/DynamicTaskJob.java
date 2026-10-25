package com.sub.schejulde.job;

import com.sub.schejulde.config.ScheduleTimeZone;
import com.sub.schejulde.domain.ScheduleHistory;
import com.sub.schejulde.repository.ScheduleHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class DynamicTaskJob extends QuartzJobBean {

    /** ScheduleHistory.message 컬럼 길이 */
    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final ScheduleHistoryRepository historyRepository;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        String jobName = context.getJobDetail().getKey().getName();
        String jobGroup = context.getJobDetail().getKey().getGroup();
        JobDataMap dataMap = context.getMergedJobDataMap();

        String taskType = dataMap.getString("taskType");
        String payload = dataMap.getString("payload");

        log.info("▶ [Quartz Job 실행 시작] group={}, name={}, taskType={}, payload={}",
                jobGroup, jobName, taskType, payload);

        String resultMessage;
        try {
            // 실제 비즈니스 로직 분기 (이메일 발송, 웹훅 호출, 데이터 정리 등)
            resultMessage = String.format("작업 수행 완료 (taskType=%s, payload=%s)",
                    taskType != null ? taskType : "DEFAULT",
                    payload != null ? payload : "{}");
        } catch (Exception e) {
            log.error("✖ [Quartz Job 실행 실패] group={}, name={}", jobGroup, jobName, e);
            saveHistory(jobName, jobGroup, "FAIL", describe(e));
            throw new JobExecutionException(e);
        }

        // 비즈니스 로직 성공 후 이력 저장 실패가 '작업 실패'로 기록되지 않도록 try 블록 밖에서 저장
        saveHistory(jobName, jobGroup, "SUCCESS", resultMessage);
        log.info("✔ [Quartz Job 실행 성공] group={}, name={}", jobGroup, jobName);
    }

    /**
     * 이력 저장 실패가 원래 작업 결과(예외)를 덮어쓰지 않도록 예외를 삼키고 로그만 남긴다.
     */
    private void saveHistory(String jobName, String jobGroup, String status, String message) {
        try {
            historyRepository.save(ScheduleHistory.builder()
                    .jobName(jobName)
                    .jobGroup(jobGroup)
                    .status(status)
                    .message(truncate(message))
                    .executedAt(LocalDateTime.now(ScheduleTimeZone.ZONE_ID))
                    .build());
        } catch (Exception historyError) {
            log.error("[실행 이력 저장 실패] group={}, name={}, status={}", jobGroup, jobName, status, historyError);
        }
    }

    private static String describe(Exception e) {
        // getMessage() 가 null 인 예외(NPE 등)도 원인을 알 수 있도록 예외 클래스명을 함께 기록
        return e.getMessage() == null
                ? e.getClass().getName()
                : e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    private static String truncate(String message) {
        if (message == null || message.length() <= MAX_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";
    }
}
