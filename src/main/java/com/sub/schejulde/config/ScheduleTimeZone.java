package com.sub.schejulde.config;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * 스케줄 기준 시간대.
 * <p>
 * 운영 컨테이너의 시간대(UTC 등)와 무관하게 모든 Cron 스케줄은 한국 시간(Asia/Seoul) 기준으로 동작한다.
 * CronTrigger의 시간대는 QRTZ_CRON_TRIGGERS.TIME_ZONE_ID 컬럼에 함께 저장되므로,
 * 클러스터 내 어떤 컨테이너가 트리거를 실행하더라도 동일한 시각에 실행된다.
 */
public final class ScheduleTimeZone {

    public static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");
    public static final TimeZone TIME_ZONE = TimeZone.getTimeZone(ZONE_ID);

    private ScheduleTimeZone() {
    }
}
