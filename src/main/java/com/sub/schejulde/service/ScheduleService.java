package com.sub.schejulde.service;

import com.sub.schejulde.config.ScheduleTimeZone;
import com.sub.schejulde.domain.ScheduleHistory;
import com.sub.schejulde.dto.ScheduleRequest;
import com.sub.schejulde.dto.ScheduleResponse;
import com.sub.schejulde.dto.ScheduleUpdateRequest;
import com.sub.schejulde.job.DynamicTaskJob;
import com.sub.schejulde.repository.ScheduleHistoryRepository;
import lombok.RequiredArgsConstructor;
import com.sub.schejulde.exception.DuplicateScheduleException;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private static final String TRIGGER_SUFFIX = "_trigger";

    private final Scheduler scheduler;
    private final ScheduleHistoryRepository historyRepository;

    /**
     * 1. 새로운 Quartz Cron 스케줄 생성
     */
    @Transactional(rollbackFor = Exception.class)
    public ScheduleResponse createSchedule(ScheduleRequest request) throws SchedulerException {
        validateCronExpression(request.cronExpression());

        String group = request.resolvedGroup();
        JobKey jobKey = JobKey.jobKey(request.jobName(), group);

        if (scheduler.checkExists(jobKey)) {
            throw new DuplicateScheduleException(group, request.jobName());
        }

        JobDetail jobDetail = buildJobDetail(jobKey, request.description(), request.jobData());
        CronTrigger trigger = buildCronTrigger(
                triggerKeyOf(jobKey), jobDetail, request.cronExpression(), request.description());

        try {
            scheduler.scheduleJob(jobDetail, trigger);
        } catch (ObjectAlreadyExistsException e) {
            // checkExists 이후 다른 요청(또는 다른 컨테이너)이 먼저 같은 키로 등록한 경우
            throw new DuplicateScheduleException(group, request.jobName());
        }
        return buildScheduleResponse(jobKey);
    }

    /**
     * 2. 전체 스케줄 목록 조회
     */
    public List<ScheduleResponse> getAllSchedules() throws SchedulerException {
        List<ScheduleResponse> responses = new ArrayList<>();
        for (String groupName : scheduler.getJobGroupNames()) {
            for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName))) {
                responses.add(buildScheduleResponse(jobKey));
            }
        }
        return responses;
    }

    /**
     * 3. 단건 스케줄 상세 조회
     */
    public ScheduleResponse getSchedule(String jobGroup, String jobName) throws SchedulerException {
        return buildScheduleResponse(ensureExists(jobGroup, jobName));
    }

    /**
     * 4. 스케줄 Cron 주기 및 데이터 수정 (Reschedule)
     * <p>
     * JobDetail 교체와 Trigger 교체를 하나의 트랜잭션으로 묶어, 중간 실패 시 함께 롤백한다.
     * (Spring Boot Quartz 는 DataSource 사용 시 LocalDataSourceJobStore 를 사용하며 Spring 트랜잭션에 참여한다)
     * SchedulerException 은 checked 예외라 기본 설정으로는 롤백되지 않으므로 rollbackFor = Exception.class 를 지정한다.
     */
    @Transactional(rollbackFor = Exception.class)
    public ScheduleResponse updateSchedule(String jobGroup, String jobName, ScheduleUpdateRequest request)
            throws SchedulerException {
        validateCronExpression(request.cronExpression());

        JobKey jobKey = ensureExists(jobGroup, jobName);
        TriggerKey triggerKey = triggerKeyOf(jobKey);

        // rescheduleJob 은 새 트리거를 NORMAL(WAITING) 상태로 저장하므로, 기존 일시정지 상태를 기억해 두었다가 복원한다
        boolean wasPaused = scheduler.getTriggerState(triggerKey) == Trigger.TriggerState.PAUSED;

        // JobDetail 덮어쓰기 (replace = true)
        JobDetail updatedJobDetail = buildJobDetail(jobKey, request.description(), request.jobData());
        scheduler.addJob(updatedJobDetail, true);

        CronTrigger newTrigger = buildCronTrigger(
                triggerKey, updatedJobDetail, request.cronExpression(), request.description());
        scheduler.rescheduleJob(triggerKey, newTrigger);

        if (wasPaused) {
            scheduler.pauseTrigger(triggerKey);
        }
        return buildScheduleResponse(jobKey);
    }

    /**
     * 5. 스케줄 일시정지 (Pause)
     */
    public ScheduleResponse pauseSchedule(String jobGroup, String jobName) throws SchedulerException {
        JobKey jobKey = ensureExists(jobGroup, jobName);
        scheduler.pauseJob(jobKey);
        return buildScheduleResponse(jobKey);
    }

    /**
     * 6. 스케줄 재개 (Resume)
     */
    public ScheduleResponse resumeSchedule(String jobGroup, String jobName) throws SchedulerException {
        JobKey jobKey = ensureExists(jobGroup, jobName);
        scheduler.resumeJob(jobKey);
        return buildScheduleResponse(jobKey);
    }

    /**
     * 7. 스케줄 즉시 1회 실행 (Trigger Now)
     */
    public void runNow(String jobGroup, String jobName) throws SchedulerException {
        JobKey jobKey = ensureExists(jobGroup, jobName);
        scheduler.triggerJob(jobKey);
    }

    /**
     * 8. 스케줄 삭제
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSchedule(String jobGroup, String jobName) throws SchedulerException {
        JobKey jobKey = ensureExists(jobGroup, jobName);
        scheduler.deleteJob(jobKey);
    }

    /**
     * 9. 실행 이력 조회 (최근 50건)
     */
    public List<ScheduleHistory> getRecentHistories() {
        return historyRepository.findTop50ByOrderByExecutedAtDesc();
    }

    private JobKey ensureExists(String jobGroup, String jobName) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        if (!scheduler.checkExists(jobKey)) {
            throw new NoSuchElementException(
                    String.format("스케줄을 찾을 수 없습니다: [%s.%s]", jobGroup, jobName)
            );
        }
        return jobKey;
    }

    private void validateCronExpression(String cronExpression) {
        if (!CronExpression.isValidExpression(cronExpression)) {
            throw new IllegalArgumentException("유효하지 않은 Quartz Cron 표현식입니다: " + cronExpression);
        }
    }

    private TriggerKey triggerKeyOf(JobKey jobKey) {
        return TriggerKey.triggerKey(jobKey.getName() + TRIGGER_SUFFIX, jobKey.getGroup());
    }

    private JobDetail buildJobDetail(JobKey jobKey, String description, Map<String, String> jobData) {
        JobDataMap jobDataMap = new JobDataMap();
        if (jobData != null) {
            jobDataMap.putAll(jobData);
        }
        return JobBuilder.newJob(DynamicTaskJob.class)
                .withIdentity(jobKey)
                .withDescription(description)
                .usingJobData(jobDataMap)
                .storeDurably()
                .build();
    }

    /**
     * 컨테이너 시간대와 무관하게 항상 한국 시간(Asia/Seoul) 기준으로 Cron 을 해석한다.
     */
    private CronTrigger buildCronTrigger(TriggerKey triggerKey, JobDetail jobDetail,
                                         String cronExpression, String description) {
        return TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .withDescription(description)
                .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression)
                        .inTimeZone(ScheduleTimeZone.TIME_ZONE)
                        .withMisfireHandlingInstructionDoNothing())
                .forJob(jobDetail)
                .build();
    }

    private ScheduleResponse buildScheduleResponse(JobKey jobKey) throws SchedulerException {
        JobDetail jobDetail = scheduler.getJobDetail(jobKey);
        TriggerKey triggerKey = triggerKeyOf(jobKey);
        // getTriggersOfJob() 은 run-now 시 생성되는 일회성 트리거도 포함하므로, Cron 트리거를 키로 직접 조회한다
        Trigger trigger = scheduler.getTrigger(triggerKey);

        String cronExp = null;
        String timeZone = null;
        String triggerState = "NONE";
        LocalDateTime nextFireTime = null;
        LocalDateTime prevFireTime = null;

        if (trigger != null) {
            triggerState = scheduler.getTriggerState(triggerKey).name();
            nextFireTime = toKstDateTime(trigger.getNextFireTime());
            prevFireTime = toKstDateTime(trigger.getPreviousFireTime());

            if (trigger instanceof CronTrigger cronTrigger) {
                cronExp = cronTrigger.getCronExpression();
                timeZone = cronTrigger.getTimeZone().getID();
            }
        }

        Map<String, String> dataMap = new HashMap<>();
        if (jobDetail.getJobDataMap() != null) {
            jobDetail.getJobDataMap().forEach((key, value) -> dataMap.put(key, String.valueOf(value)));
        }

        return ScheduleResponse.builder()
                .jobName(jobKey.getName())
                .jobGroup(jobKey.getGroup())
                .description(jobDetail.getDescription())
                .cronExpression(cronExp)
                .timeZone(timeZone)
                .triggerState(triggerState)
                .nextFireTime(nextFireTime)
                .previousFireTime(prevFireTime)
                .jobData(dataMap)
                .build();
    }

    /**
     * 응답 시각도 JVM 시간대가 아닌 한국 시간으로 변환한다.
     */
    private LocalDateTime toKstDateTime(Date date) {
        if (date == null) return null;
        return LocalDateTime.ofInstant(date.toInstant(), ScheduleTimeZone.ZONE_ID);
    }
}
