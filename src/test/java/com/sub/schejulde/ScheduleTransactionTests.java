package com.sub.schejulde;

import com.sub.schejulde.dto.ScheduleRequest;
import com.sub.schejulde.dto.ScheduleResponse;
import com.sub.schejulde.dto.ScheduleUpdateRequest;
import com.sub.schejulde.service.ScheduleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 스케줄 수정 중 트랜잭션이 롤백되면 JobDetail(jobData/description) 변경까지 함께 롤백되는지 검증.
 */
@SpringBootTest
class ScheduleTransactionTests {

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("트랜잭션 내 스케줄 수정 후 예외가 발생하면 변경된 jobData 와 Cron 이 모두 롤백된다")
    void updateRollsBackOnTransactionFailure() throws Exception {
        scheduleService.createSchedule(new ScheduleRequest(
                "txJob", "TX", "0/30 * * * * ?", "원래 설명", Map.of("payload", "original")));

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> txTemplate.executeWithoutResult(status -> {
            try {
                scheduleService.updateSchedule("TX", "txJob", new ScheduleUpdateRequest(
                        "0/10 * * * * ?", "바뀐 설명", Map.of("payload", "changed")));
                throw new RuntimeException("강제 트랜잭션 실패");
            } catch (Exception e) {
                if (e instanceof RuntimeException re) throw re;
                throw new RuntimeException(e);
            }
        })).isInstanceOf(RuntimeException.class);

        ScheduleResponse after = scheduleService.getSchedule("TX", "txJob");
        assertThat(after.description()).isEqualTo("원래 설명");
        assertThat(after.jobData()).containsEntry("payload", "original");
        assertThat(after.cronExpression()).isEqualTo("0/30 * * * * ?");

        scheduleService.deleteSchedule("TX", "txJob");
    }
}
