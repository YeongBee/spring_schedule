package com.sub.schejulde;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sub.schejulde.dto.ScheduleRequest;
import com.sub.schejulde.dto.ScheduleUpdateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SchejuldeApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("웹에서 동적 스케줄 생성, 조회, 수정, 일시정지, 재개, 삭제가 H2 기반으로 동작한다")
    void scheduleLifecycleTest() throws Exception {
        ScheduleRequest request = new ScheduleRequest(
                "dailyReportJob",
                "REPORT",
                "0/30 * * * * ?",
                "30초마다 리포트 생성",
                Map.of("taskType", "EMAIL_REPORT", "payload", "{\"target\":\"admin@sub.com\"}")
        );

        // 1. 스케줄 생성 (POST /api/schedules)
        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobName").value("dailyReportJob"))
                .andExpect(jsonPath("$.jobGroup").value("REPORT"))
                .andExpect(jsonPath("$.timeZone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.triggerState").value("NORMAL"));

        // 2. 스케줄 목록 조회 (GET /api/schedules)
        mockMvc.perform(get("/api/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobName").value("dailyReportJob"));

        // 3. 일시정지 (POST /api/schedules/REPORT/dailyReportJob/pause)
        mockMvc.perform(post("/api/schedules/REPORT/dailyReportJob/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.triggerState").value("PAUSED"));

        // 4. 일시정지 상태에서 수정 (PUT /api/schedules/REPORT/dailyReportJob) -> PAUSED 상태 유지되어야 함
        ScheduleUpdateRequest updateRequest = new ScheduleUpdateRequest(
                "0/10 * * * * ?",
                "10초마다 리포트 생성으로 변경",
                Map.of("taskType", "EMAIL_REPORT", "payload", "{\"target\":\"ops@sub.com\"}")
        );
        mockMvc.perform(put("/api/schedules/REPORT/dailyReportJob")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cronExpression").value("0/10 * * * * ?"))
                .andExpect(jsonPath("$.description").value("10초마다 리포트 생성으로 변경"))
                .andExpect(jsonPath("$.timeZone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.triggerState").value("PAUSED"));

        // 5. 재개 (POST /api/schedules/REPORT/dailyReportJob/resume)
        mockMvc.perform(post("/api/schedules/REPORT/dailyReportJob/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.triggerState").value("NORMAL"));

        // 6. 삭제 (DELETE /api/schedules/REPORT/dailyReportJob)
        mockMvc.perform(delete("/api/schedules/REPORT/dailyReportJob"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("JVM 시간대가 UTC 여도 Cron 은 한국 시간(Asia/Seoul) 기준으로 동작한다")
    void cronRunsInKoreanTimeZoneRegardlessOfJvmTimeZone() throws Exception {
        // build.gradle 에서 테스트 JVM 을 UTC 로 실행 (운영 컨테이너 가정)
        org.assertj.core.api.Assertions.assertThat(java.util.TimeZone.getDefault().getID()).isEqualTo("UTC");

        ScheduleRequest request = new ScheduleRequest(
                "morningJob", "TZ_TEST", "0 0 9 * * ?", "매일 오전 9시", Map.of());

        mockMvc.perform(post("/api/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timeZone").value("Asia/Seoul"))
                // UTC 기준이었다면 한국 시간 18:00 으로 표시됨
                .andExpect(jsonPath("$.nextFireTime").value(org.hamcrest.Matchers.containsString("T09:00")));

        mockMvc.perform(delete("/api/schedules/TZ_TEST/morningJob"))
                .andExpect(status().isNoContent());
    }
}
