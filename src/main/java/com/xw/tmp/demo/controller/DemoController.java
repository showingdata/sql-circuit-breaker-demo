package com.xw.tmp.demo.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xw.tmp.demo.entity.Order;
import com.xw.tmp.demo.service.OrderService;
import io.github.showingdata.starter.framework.circuitbreaker.SqlCircuitBreakerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示接口：模拟超时熔断的完整生命周期
 *
 * 推荐测试步骤：
 *  1. GET /demo/slow?seconds=3   → 第1次：TIMEOUT + CIRCUIT_OPEN，日志打印熔断事件
 *  2. GET /demo/slow?seconds=3   → 第2次：FAST_FAIL，立即返回 503，不发 SQL
 *  3. GET /demo/list             → 正常查询不受影响（不同 SQL 指纹，独立熔断）
 *  4. 等待 30s（circuit-open-ms）后再次调 /demo/slow → 探针放行
 *     - 若仍慢：重新熔断（等 half-open-probe-delay-ms=10s 后再探）
 *     - 若正常：RECOVERED，熔断解除
 *  5. GET /demo/slow-bypass?seconds=3 → ThreadLocal 覆盖超时为 10s，不触发熔断
 *  6. GET /demo/status-query      → 方法级注解超时 5s，不受接口级 1s 限制
 *
 *
 *  ──────┬────────────────────────────────────┬──────────────────────────────────────────────────────────┐
 *   │ 步骤 │                请求                │                         预期结果                         │
 *   ├──────┼────────────────────────────────────┼──────────────────────────────────────────────────────────┤
 *   │ 1    │ GET /demo/list                     │ 正常返回订单列表                                         │
 *   ├──────┼────────────────────────────────────┼──────────────────────────────────────────────────────────┤
 *   │ 2    │ GET /demo/slow?seconds=3           │ 3s 后返回，日志出现 TIMEOUT + CIRCUIT_OPEN，消息中心打印 │
 *   ├──────┼────────────────────────────────────┼──────────────────────────────────────────────────────────┤
 *   │ 3    │ 立刻再次 GET /demo/slow             │ 立即返回 503，日志出现 FAST_FAIL，不等 3s                │
 *   ├──────┼────────────────────────────────────┼──────────────────────────────────────────────────────────┤
 *   │ 4    │ GET /demo/list                     │ 仍然正常，独立指纹不受影响                               │
 *   ├──────┼────────────────────────────────────┼──────────────────────────────────────────────────────────┤
 *   │ 5    │ GET /demo/slow-bypass?seconds=3    │ 3s 后正常返回，ThreadLocal 覆盖超时 10s 不熔断           │
 *   ├──────┼────────────────────────────────────┼──────────────────────────────────────────────────────────┤
 *   │ 6    │ GET /demo/status-query             │ 正常返回，方法级注解 5s 超时                             │
 *   ├──────┼────────────────────────────────────┼──────────────────────────────────────────────────────────┤
 *   │ 7    │ 等 30s 后 GET /demo/slow?seconds=0 │ 探针放行 → 执行成功 → RECOVERED，熔断解除                │
 *   └──────┴────────────────────────────────────┴──────────────────────────────────────────────────────────┘
 */
@Slf4j
@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

    private final OrderService orderService;

    /**
     * 触发慢查询：seconds > 1 时必然超时，第一次 CIRCUIT_OPEN，后续 FAST_FAIL
     */
    @GetMapping("/slow")
    public ResponseEntity<Map<String, Object>> triggerSlow(@RequestParam(defaultValue = "3") int seconds) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        try {
            orderService.simulateSlowQuery(seconds);
            result.put("status", "success");
            result.put("cost", System.currentTimeMillis() - start + "ms");
            result.put("msg", "查询完成（未触发熔断，检查日志是否有 TIMEOUT）");
            return ResponseEntity.ok(result);
        } catch (SqlCircuitBreakerException e) {
            result.put("status", "circuit_open");
            result.put("cost", System.currentTimeMillis() - start + "ms");
            result.put("msg", "熔断器已打开，快速失败！SQL 未发送到 DB");
            result.put("circuitKey", e.getCircuitKey());
            log.warn("[Demo] 请求被熔断拦截: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);
        }
    }

    /**
     * 正常查询：SQL 指纹不同，有独立熔断状态，不受 /slow 熔断影响
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listOrders() {
        long start = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        try {
            List<Order> orders = orderService.listAll();
            result.put("status", "success");
            result.put("cost", System.currentTimeMillis() - start + "ms");
            result.put("count", orders.size());
            result.put("data", orders);
            return ResponseEntity.ok(result);
        } catch (SqlCircuitBreakerException e) {
            result.put("status", "circuit_open");
            result.put("circuitKey", e.getCircuitKey());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);
        }
    }

    /**
     * 按用户查询：演示接口级注解，超时 1s
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Map<String, Object>> listByUser(@PathVariable Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Order> orders = orderService.listByUser(userId);
            result.put("status", "success");
            result.put("data", orders);
            return ResponseEntity.ok(result);
        } catch (SqlCircuitBreakerException e) {
            result.put("status", "circuit_open");
            result.put("circuitKey", e.getCircuitKey());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);
        }
    }

    /**
     * 方法级注解覆盖：selectByStatus 单独配置超时 5s，演示注解优先级
     */
    @GetMapping("/status-query")
    public ResponseEntity<Map<String, Object>> queryByStatus(
            @RequestParam(defaultValue = "0") Integer status) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Order> orders = orderService.listByStatus(status);
            result.put("status", "success");
            result.put("msg", "方法级注解超时 5s，比接口级 1s 更宽松");
            result.put("data", orders);
            return ResponseEntity.ok(result);
        } catch (SqlCircuitBreakerException e) {
            result.put("status", "circuit_open");
            result.put("circuitKey", e.getCircuitKey());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);
        }
    }

    /**
     * 模拟 SQL 异常（表不存在）：
     * - 拦截器 catch(Throwable t) 直接透传，不触发熔断、不增加超时计数
     * - 连续调用多次，/demo/list 等正常接口不受任何影响
     * - 返回的是原始 BadSqlGrammarException，不是 SqlCircuitBreakerException
     */
    @GetMapping("/sql-error")
    public ResponseEntity<Map<String, Object>> triggerSqlError() {
        Map<String, Object> result = new HashMap<>();
        try {
            orderService.simulateSqlException(1L);
            result.put("status", "success");
            return ResponseEntity.ok(result);
        } catch (SqlCircuitBreakerException e) {
            // 不会走到这里，SQL 异常不经过熔断器
            result.put("status", "circuit_open");
            result.put("circuitKey", e.getCircuitKey());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);
        } catch (Exception e) {
            // SQL 异常直接透传到这里
            result.put("status", "sql_error");
            result.put("errorType", e.getClass().getSimpleName());
            result.put("msg", "SQL 执行异常直接透传，熔断器未介入，circuitBreaker 计数不增加");
            result.put("cause", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            log.error("[Demo] SQL 异常透传（非熔断）: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * ThreadLocal 覆盖演示：临时将超时放宽至 10s，相同慢查询不触发熔断
     */
    @GetMapping("/slow-bypass")
    public ResponseEntity<Map<String, Object>> triggerSlowWithBypass(@RequestParam(defaultValue = "3") int seconds) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        try {
            orderService.simulateSlowQueryWithLongerTimeout(seconds);
            result.put("status", "success");
            result.put("cost", System.currentTimeMillis() - start + "ms");
            result.put("msg", "ThreadLocal 覆盖超时 10s，" + seconds + "s 慢查询未触发熔断");
            return ResponseEntity.ok(result);
        } catch (SqlCircuitBreakerException e) {
            result.put("status", "circuit_open");
            result.put("circuitKey", e.getCircuitKey());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);
        }
    }

    /**
     * 分页查询演示。
     *
     * MP 分页原理：每次分页请求会向 DB 发送两条 SQL——
     *   ① SELECT COUNT(*) FROM t_order WHERE ...        （总数，独立熔断 Key）
     *   ② SELECT * FROM t_order WHERE ... LIMIT ?,?     （数据，独立熔断 Key）
     * 两条 SQL 的指纹不同，各自独立计入超时次数，互不影响。
     * 注意：COUNT 失败不会阻止数据 SQL 执行（两者串行，COUNT 先行）。
     *
     * 测试步骤：
     *  1. GET /demo/page?page=1&size=3              → 第1页，每页3条
     *  2. GET /demo/page?page=2&size=3              → 第2页
     *  3. GET /demo/page?page=1&size=5&status=1     → 过滤已支付订单，每页5条
     *  4. GET /demo/page?page=99&size=3             → 超出范围，返回空 records
     */
    @GetMapping("/page")
    public ResponseEntity<Map<String, Object>> pageOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "3") int size,
            @RequestParam(required = false) Integer status) {
        Map<String, Object> result = new HashMap<>();
        try {
            IPage<Order> pageResult = orderService.pageByStatus(page, size, status);
            result.put("status", "success");
            result.put("current", pageResult.getCurrent());
            result.put("size", pageResult.getSize());
            result.put("total", pageResult.getTotal());
            result.put("pages", pageResult.getPages());
            result.put("records", pageResult.getRecords());
            return ResponseEntity.ok(result);
        } catch (SqlCircuitBreakerException e) {
            result.put("status", "circuit_open");
            result.put("circuitKey", e.getCircuitKey());
            result.put("msg", "分页查询被熔断（COUNT 或 SELECT 超时达到阈值）");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);
        }
    }

    /**
     * disableCircuitBreaker 演示：完全跳过熔断检测，穿透已 OPEN 的熔断器直达 DB。
     *
     * 推荐测试步骤：
     *  1. GET /demo/slow?seconds=3         → 触发熔断，熔断器变 OPEN
     *  2. GET /demo/slow?seconds=3         → 立即 FAST_FAIL（证明熔断器已开启）
     *  3. GET /demo/repair?seconds=3       → 正常执行 3s 后返回（穿透了 OPEN 的熔断器）
     *  4. GET /demo/slow?seconds=3         → 仍然 FAST_FAIL（repair 不影响熔断状态）
     */
    @GetMapping("/repair")
    public ResponseEntity<Map<String, Object>> repairWithDisabledCircuitBreaker(
            @RequestParam(defaultValue = "3") int seconds) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        try {
            orderService.repairDataWithCircuitBreakerDisabled(seconds);
            result.put("status", "success");
            result.put("cost", System.currentTimeMillis() - start + "ms");
            result.put("msg", "disableCircuitBreaker=true，穿透熔断器直接执行，SQL 耗时 " + seconds + "s，失败计数不增加");
            return ResponseEntity.ok(result);
        } catch (SqlCircuitBreakerException e) {
            // disableCircuitBreaker=true 时永远不会走到这里
            result.put("status", "circuit_open");
            result.put("circuitKey", e.getCircuitKey());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);
        }
    }
}
