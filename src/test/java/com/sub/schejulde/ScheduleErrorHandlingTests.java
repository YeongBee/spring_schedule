package com.sub.schejulde;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sub.schejulde.dto.ScheduleRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ScheduleErrorHandlingTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("잘못된 Cron 표현식은 400")
    void invalidCron() throws Exception {
        create(new ScheduleRequest("badCronJob", "ERR", "every 10 seconds", null, Map.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("유효하지 않은 Quartz Cron 표현식")));
    }

    @Test
    @DisplayName("jobName 에 경로 구분자 등 허용되지 않은 문자가 있으면 400 + 필드별 상세")
    void invalidJobName() throws Exception {
        create(new ScheduleRequest("bad/name", "ERR", "0/10 * * * * ?", null, Map.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("jobName")))
                .andExpect(jsonPath("$.details").value(hasItem(containsString("영문, 숫자"))));
    }

    @Test
    @DisplayName("jobData 값에 문자열이 아닌 객체를 보내면 400 (String 키-값만 허용)")
    void nonStringJobData() throws Exception {
        String body = """
                {"jobName":"objDataJob","jobGroup":"ERR","cronExpression":"0/10 * * * * ?",
                 "jobData":{"payload":{"target":"admin@sub.com"}}}
                """;
        mockMvc.perform(post("/api/schedules").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("요청 본문(JSON) 형식이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("이미 존재하는 스케줄을 다시 생성하면 409")
    void duplicate() throws Exception {
        ScheduleRequest request = new ScheduleRequest("dupJob", "ERR", "0/10 * * * * ?", null, Map.of());
        create(request).andExpect(status().isCreated());

        create(request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("이미 동일한 스케줄이 존재합니다")));

        mockMvc.perform(delete("/api/schedules/ERR/dupJob")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("존재하지 않는 스케줄 조회/수정/삭제는 404")
    void notFound() throws Exception {
        mockMvc.perform(get("/api/schedules/ERR/noSuchJob"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(containsString("스케줄을 찾을 수 없습니다")));

        mockMvc.perform(put("/api/schedules/ERR/noSuchJob")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cronExpression\":\"0/10 * * * * ?\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/schedules/ERR/noSuchJob"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드는 500 이 아닌 405 를 유지한다")
    void methodNotAllowed() throws Exception {
        mockMvc.perform(patch("/api/schedules"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").exists());
    }

    private org.springframework.test.web.servlet.ResultActions create(ScheduleRequest request) throws Exception {
        return mockMvc.perform(post("/api/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }
}
