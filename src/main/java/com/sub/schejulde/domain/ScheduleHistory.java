package com.sub.schejulde.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "schedule_execution_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ScheduleHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "schedule_history_seq_gen")
    @SequenceGenerator(
            name = "schedule_history_seq_gen",
            sequenceName = "SEQ_SCHEDULE_HISTORY",
            allocationSize = 1
    )
    private Long id;

    @Column(nullable = false)
    private String jobName;

    @Column(nullable = false)
    private String jobGroup;

    @Column(nullable = false)
    private String status; // SUCCESS, FAIL

    @Column(length = 1000)
    private String message;

    @Column(nullable = false)
    private LocalDateTime executedAt;
}
