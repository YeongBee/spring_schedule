package com.sub.schejulde.exception;

import lombok.extern.slf4j.Slf4j;
import org.quartz.SchedulerException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 전역 예외 처리.
 * 모든 에러 응답은 {"error": "메시지"} 형태로 통일한다. (검증 실패 시 "details" 추가)
 * 서버 내부 오류(5xx)는 상세 원인을 로그로만 남기고 클라이언트에는 노출하지 않는다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** @Valid 검증 실패 -> 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", details.isEmpty() ? "입력값이 올바르지 않습니다." : String.join(", ", details));
        body.put("details", details);
        return ResponseEntity.badRequest().body(body);
    }

    /** JSON 형식 오류, 타입 불일치(예: jobData 값에 객체 전달) -> 400 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "요청 본문(JSON) 형식이 올바르지 않습니다.");
    }

    /** 잘못된 Cron 표현식 등 -> 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** 존재하지 않는 스케줄 -> 404 */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** 중복 스케줄 -> 409 */
    @ExceptionHandler(DuplicateScheduleException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateScheduleException e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Quartz 내부 오류 -> 500 (상세 원인은 로그로만) */
    @ExceptionHandler(SchedulerException.class)
    public ResponseEntity<Map<String, Object>> handleSchedulerException(SchedulerException e) {
        log.error("[Scheduler 오류]", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "스케줄러 처리 중 오류가 발생했습니다.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
        // 405, 415, 존재하지 않는 경로(404) 등 Spring MVC 표준 예외는 원래 상태 코드를 유지한다
        if (e instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            String detail = errorResponse.getBody().getDetail();
            return error(status, detail != null ? detail : "요청을 처리할 수 없습니다.");
        }
        log.error("[예상하지 못한 오류]", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatusCode status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }
}
