package com.sub.schejulde.controller;

import com.sub.schejulde.domain.ScheduleHistory;
import com.sub.schejulde.dto.ScheduleRequest;
import com.sub.schejulde.dto.ScheduleResponse;
import com.sub.schejulde.dto.ScheduleUpdateRequest;
import com.sub.schejulde.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.quartz.SchedulerException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    /**
     * 스케줄 생성 (POST /api/schedules)
     */
    @PostMapping
    public ResponseEntity<ScheduleResponse> createSchedule(@Valid @RequestBody ScheduleRequest request)
            throws SchedulerException {
        ScheduleResponse created = scheduleService.createSchedule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * 전체 스케줄 목록 조회 (GET /api/schedules)
     */
    @GetMapping
    public ResponseEntity<List<ScheduleResponse>> getAllSchedules() throws SchedulerException {
        return ResponseEntity.ok(scheduleService.getAllSchedules());
    }

    /**
     * 단건 스케줄 조회 (GET /api/schedules/{jobGroup}/{jobName})
     */
    @GetMapping("/{jobGroup}/{jobName}")
    public ResponseEntity<ScheduleResponse> getSchedule(
            @PathVariable String jobGroup,
            @PathVariable String jobName) throws SchedulerException {
        return ResponseEntity.ok(scheduleService.getSchedule(jobGroup, jobName));
    }

    /**
     * 스케줄 수정 (PUT /api/schedules/{jobGroup}/{jobName})
     */
    @PutMapping("/{jobGroup}/{jobName}")
    public ResponseEntity<ScheduleResponse> updateSchedule(
            @PathVariable String jobGroup,
            @PathVariable String jobName,
            @Valid @RequestBody ScheduleUpdateRequest request)
            throws SchedulerException {
        return ResponseEntity.ok(scheduleService.updateSchedule(jobGroup, jobName, request));
    }

    /**
     * 스케줄 일시정지 (POST /api/schedules/{jobGroup}/{jobName}/pause)
     */
    @PostMapping("/{jobGroup}/{jobName}/pause")
    public ResponseEntity<ScheduleResponse> pauseSchedule(
            @PathVariable String jobGroup,
            @PathVariable String jobName) throws SchedulerException {
        return ResponseEntity.ok(scheduleService.pauseSchedule(jobGroup, jobName));
    }

    /**
     * 스케줄 재개 (POST /api/schedules/{jobGroup}/{jobName}/resume)
     */
    @PostMapping("/{jobGroup}/{jobName}/resume")
    public ResponseEntity<ScheduleResponse> resumeSchedule(
            @PathVariable String jobGroup,
            @PathVariable String jobName) throws SchedulerException {
        return ResponseEntity.ok(scheduleService.resumeSchedule(jobGroup, jobName));
    }

    /**
     * 스케줄 즉시 실행 (POST /api/schedules/{jobGroup}/{jobName}/run-now)
     */
    @PostMapping("/{jobGroup}/{jobName}/run-now")
    public ResponseEntity<Map<String, String>> runNow(
            @PathVariable String jobGroup,
            @PathVariable String jobName) throws SchedulerException {
        scheduleService.runNow(jobGroup, jobName);
        return ResponseEntity.ok(Map.of("message", "스케줄 즉시 실행이 요청되었습니다."));
    }

    /**
     * 스케줄 삭제 (DELETE /api/schedules/{jobGroup}/{jobName})
     */
    @DeleteMapping("/{jobGroup}/{jobName}")
    public ResponseEntity<Void> deleteSchedule(
            @PathVariable String jobGroup,
            @PathVariable String jobName) throws SchedulerException {
        scheduleService.deleteSchedule(jobGroup, jobName);
        return ResponseEntity.noContent().build();
    }

    /**
     * 스케줄 실행 이력 조회 (GET /api/schedules/histories)
     */
    @GetMapping("/histories")
    public ResponseEntity<List<ScheduleHistory>> getHistories() {
        return ResponseEntity.ok(scheduleService.getRecentHistories());
    }
}
