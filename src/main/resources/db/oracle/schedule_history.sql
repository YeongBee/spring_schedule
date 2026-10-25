-- ==========================================================
-- 스케줄 실행 이력 테이블 / 시퀀스 (Oracle, 운영 최초 1회 실행)
-- ScheduleHistory 엔티티 매핑과 일치해야 함 (ddl-auto=validate)
-- ==========================================================
CREATE SEQUENCE SEQ_SCHEDULE_HISTORY START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE TABLE SCHEDULE_EXECUTION_HISTORY (
    ID          NUMBER(19, 0)       NOT NULL,
    JOB_NAME    VARCHAR2(255 CHAR)  NOT NULL,
    JOB_GROUP   VARCHAR2(255 CHAR)  NOT NULL,
    STATUS      VARCHAR2(255 CHAR)  NOT NULL,
    MESSAGE     VARCHAR2(1000 CHAR),
    EXECUTED_AT TIMESTAMP(6)        NOT NULL,
    CONSTRAINT PK_SCHEDULE_EXECUTION_HISTORY PRIMARY KEY (ID)
);

CREATE INDEX IDX_SCHED_HIST_EXECUTED_AT ON SCHEDULE_EXECUTION_HISTORY (EXECUTED_AT DESC);
