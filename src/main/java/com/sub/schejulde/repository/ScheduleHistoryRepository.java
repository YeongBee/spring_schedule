package com.sub.schejulde.repository;

import com.sub.schejulde.domain.ScheduleHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduleHistoryRepository extends JpaRepository<ScheduleHistory, Long> {
    List<ScheduleHistory> findTop50ByOrderByExecutedAtDesc();
    List<ScheduleHistory> findByJobNameAndJobGroupOrderByExecutedAtDesc(String jobName, String jobGroup);
}
