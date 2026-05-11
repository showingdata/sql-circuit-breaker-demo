package io.github.showingdata.sql.circuit.breaker.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.showingdata.sql.circuit.breaker.entity.Order;
import io.github.showingdata.sql.circuit.breaker.mapper.OrderMapper;
import io.github.showingdata.starter.framework.circuitbreaker.context.SqlCircuitBreakerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;

    /**
     * 查询所有订单（走 BaseMapper，受接口级熔断保护）
     */
    public List<Order> listAll() {
        return orderMapper.selectList(null);
    }

    /**
     * 按用户查询（受接口级熔断保护，超时 1s）
     */
    public List<Order> listByUser(Long userId) {
        return orderMapper.selectByUserId(userId);
    }

    /**
     * 按状态查询（受方法级熔断保护，超时 5s）
     */
    public List<Order> listByStatus(Integer status) {
        return orderMapper.selectByStatus(status);
    }

    /**
     * 模拟慢查询：执行 SELECT SLEEP(seconds)，超过 1s 阈值即触发熔断。
     * 连续调用两次：第一次 TIMEOUT + CIRCUIT_OPEN；第二次 FAST_FAIL。
     */
    public void simulateSlowQuery(int seconds) {
        log.info("[Demo] 执行慢查询，睡眠 {} 秒...", seconds);
        orderMapper.simulateSlowQuery(seconds);
    }

    /**
     * 模拟 SQL 异常：查询不存在的表，异常直接透传给调用方，熔断计数不增加。
     * 连续调用 N 次也不会触发熔断，与超时熔断形成对比。
     */
    public void simulateSqlException(Long id) {
        log.info("[Demo] 执行异常查询（表不存在），验证异常透传...");
        orderMapper.selectFromNonExistTable(id);
    }

    /**
     * 演示 ThreadLocal 编程式覆盖：临时放宽超时到 10s，绕过默认 1s 阈值
     */
    public void simulateSlowQueryWithLongerTimeout(int seconds) {
        try {
            SqlCircuitBreakerContext.setTimeout(10);
            log.info("[Demo] ThreadLocal 覆盖超时为 10s，执行慢查询 {} 秒...", seconds);
            orderMapper.simulateSlowQuery(seconds);
        } finally {
            SqlCircuitBreakerContext.clear();
        }
    }

    /**
     * 分页查询，支持可选状态过滤。
     * status 为 null 时查所有状态。
     *
     * MP 自动生成 COUNT + SELECT 两条 SQL，各自独立受熔断器保护：
     *   ① SELECT COUNT(*) ...   → 走 selectPageByStatus 方法注解 5000ms 超时
     *   ② SELECT * ... LIMIT?   → 同上（注意：SDK 拦截器 finally 在每条 SQL 后都会 clear ThreadLocal，
     *                             所以若在业务层用 SqlCircuitBreakerContext.setXxx() 只能影响第 ① 条）
     * 若想模拟分页超时触发熔断，推荐直接缩小注解的 selectTimeout 或通过 SLEEP 慢查询来演示。
     */
    @DS("slave_1")
    public IPage<Order> pageByStatus(int pageNum, int pageSize, Integer status) {
        IPage<Order> page = new Page(pageNum, pageSize);
        return orderMapper.selectPageByStatus(page, status);
    }


    /**
     * 演示 disableCircuitBreaker=true：完全跳过熔断检测。
     * 即使该 SQL 指纹的熔断器已经 OPEN，该调用仍会穿透直达 DB，且不计入失败次数。
     * 场景：定时任务补偿、人工数据修复等明知 SQL 会慢但不希望触发熔断的操作。
     */
    public void repairDataWithCircuitBreakerDisabled(int seconds) {
        try {
            SqlCircuitBreakerContext.disableCircuitBreaker();
            log.info("[Demo] disableCircuitBreaker=true，跳过熔断直接执行慢查询 {} 秒...", seconds);
            orderMapper.simulateSlowQuery(seconds);
        } finally {
            SqlCircuitBreakerContext.clear();
        }
    }
}
