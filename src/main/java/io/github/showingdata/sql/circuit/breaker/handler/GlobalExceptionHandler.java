package io.github.showingdata.sql.circuit.breaker.handler;

import io.github.showingdata.starter.framework.circuitbreaker.SqlCircuitBreakerException;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.MyBatisSystemException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理：补全 SqlCircuitBreakerException 的业务调用栈日志。
 *
 * SDK 中 SqlCircuitBreakerException 重写了 fillInStackTrace()（高并发性能优化），
 * 异常对象本身不带 stack trace。但 MyBatis 抛出时会用 MyBatisSystemException 包装，
 * 包装异常带有完整堆栈（Controller → Service → Mapper 代理 → MyBatis 拦截器链），
 * 这里通过 log.error("...", wrapper) 打出包装栈，即可定位业务调用方代码行。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MyBatisSystemException.class)
    public ResponseEntity<Map<String, Object>> handleMyBatis(MyBatisSystemException ex) {
        SqlCircuitBreakerException cb = findCircuitBreaker(ex);
        if (cb != null) {
            log.error("[GlobalExceptionHandler] SQL 熔断快速失败 | key={} | 业务调用栈见下方", cb.getCircuitKey(), ex);
            return circuitBreakerResponse(cb);
        }
        log.error("[GlobalExceptionHandler] MyBatis 异常: {}", ex.getMessage(), ex);
        return errorResponse("db_error", ex.getMessage());
    }

    @ExceptionHandler(SqlCircuitBreakerException.class)
    public ResponseEntity<Map<String, Object>> handleCircuitBreaker(SqlCircuitBreakerException ex) {
        log.error("[GlobalExceptionHandler] SQL 熔断快速失败（无包装栈）| key={} | msg={}", ex.getCircuitKey(), ex.getMessage());
        return circuitBreakerResponse(ex);
    }
    private SqlCircuitBreakerException findCircuitBreaker(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            if (cur instanceof SqlCircuitBreakerException) {
                return (SqlCircuitBreakerException) cur;
            }
            cur = cur.getCause();
        }
        return null;
    }

    private ResponseEntity<Map<String, Object>> circuitBreakerResponse(SqlCircuitBreakerException cb) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "circuit_open");
        body.put("circuitKey", cb.getCircuitKey());
        body.put("msg", cb.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private ResponseEntity<Map<String, Object>> errorResponse(String status, String msg) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        body.put("msg", msg);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
