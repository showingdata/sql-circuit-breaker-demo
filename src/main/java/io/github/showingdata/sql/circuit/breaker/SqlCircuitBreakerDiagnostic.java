package io.github.showingdata.sql.circuit.breaker;

import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author chenjiang
 * @date 2026/7/6 15:25
 * @package io.github.showingdata.sql.circuit.breaker
 * @className SqlCircuitBreakerDiagnostic
 * @description
 */
@Component
public class SqlCircuitBreakerDiagnostic implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SqlCircuitBreakerDiagnostic.class);

    @Autowired(required = false)
    private List<SqlSessionFactory> sqlSessionFactories;

    @Autowired(required = false)
    private Interceptor sqlCircuitBreakerInterceptor;

    @Override
    public void run(ApplicationArguments args) {
        log.info("===== SQL 熔断器诊断 =====");
        log.info("熔断器拦截器 Bean: {}", sqlCircuitBreakerInterceptor != null ? "✅ 已创建" : "❌ 未创建");
        if (sqlSessionFactories == null || sqlSessionFactories.isEmpty()) {
            log.warn("未找到 SqlSessionFactory Bean");
            return;
        }
        log.info("SqlSessionFactory 数量: {}", sqlSessionFactories.size());
        for (int i = 0; i < sqlSessionFactories.size(); i++) {
            SqlSessionFactory factory = sqlSessionFactories.get(i);
            List<Interceptor> interceptors = factory.getConfiguration().getInterceptors();
            log.info("SqlSessionFactory[{}] 拦截器数量: {}", i, interceptors.size());
            interceptors.forEach(interceptor ->
                    log.info("  - {}", interceptor.getClass().getName())
            );
            boolean hasCircuitBreaker = interceptors.stream().anyMatch(interceptor -> interceptor.getClass().getName().contains("CircuitBreaker"));
            log.info("  SQL 熔断器: {}", hasCircuitBreaker ? "✅ 已注册" : "❌ 未注册");
        }
    }
}
