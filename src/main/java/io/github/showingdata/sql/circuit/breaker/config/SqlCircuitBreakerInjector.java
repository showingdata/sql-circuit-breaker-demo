package io.github.showingdata.sql.circuit.breaker.config;

import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author chenjiang
 * @date 2026/7/6 15:22
 * @package io.github.showingdata.sql.circuit.breaker.config
 * @className SqlCircuitBreakerInjector
 * @description 强制注入
 */
@Component
@ConditionalOnProperty(prefix = "sql-circuit-breaker", name = "auto-inject", havingValue = "true", matchIfMissing = true)
@SuppressWarnings("all")
@Order(Ordered.LOWEST_PRECEDENCE)
public class SqlCircuitBreakerInjector implements BeanPostProcessor {
    private static final Logger log = LoggerFactory.getLogger(SqlCircuitBreakerInjector.class);

    /**
     * SQL 熔断器拦截器（由熔断器 SDK 自动装配）
     * required = false：如果熔断器未启用或未引入，不影响启动
     */
    @Autowired(required = false)
    private Interceptor sqlCircuitBreakerInterceptor;


    /**
     * 在 Bean 初始化完成后执行
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 只处理 SqlSessionFactory 类型的 Bean
        if (!(bean instanceof SqlSessionFactory)) {
            return bean;
        }
        // 如果熔断器拦截器未注入，说明熔断器未启用或未引入，跳过
        if (sqlCircuitBreakerInterceptor == null) {
            log.info("SQL 熔断器拦截器未找到，跳过注入 [{}]", beanName);
            return bean;
        }

        SqlSessionFactory factory = (SqlSessionFactory) bean;
        Configuration configuration = factory.getConfiguration();
        List<Interceptor> interceptors = configuration.getInterceptors();

        // 检查是否已经注册过（避免重复注册）
        boolean alreadyRegistered = interceptors.stream().anyMatch(interceptor -> isSameInterceptor(interceptor, sqlCircuitBreakerInterceptor));

        if (alreadyRegistered) {
            log.info("SQL 熔断器拦截器已存在于 [{}]，跳过注入", beanName);
            return bean;
        }

        // 注入熔断器拦截器
        try {
            configuration.addInterceptor(sqlCircuitBreakerInterceptor);
            log.info("✅ SQL 熔断器拦截器已自动注入到 SqlSessionFactory [{}]", beanName);
            // 打印当前所有拦截器（方便排查问题）
            if (log.isDebugEnabled()) {
                log.debug("SqlSessionFactory [{}] 当前拦截器链:", beanName);
                configuration.getInterceptors().forEach(i ->
                        log.debug("  - {}", i.getClass().getName())
                );
            }
        } catch (Exception e) {
            log.error("❌ 注入 SQL 熔断器拦截器到 [{}] 失败", beanName, e);
        }
        return bean;
    }

    /**
     * 判断两个拦截器是否为同一个实例或同一类型
     */
    private boolean isSameInterceptor(Interceptor interceptor1, Interceptor interceptor2) {
        // 先判断是否为同一个对象实例
        if (interceptor1 == interceptor2) {
            return true;
        }
        // 再判断是否为同一个类型（防止类名相同但不同实例的情况）
        return interceptor1.getClass().equals(interceptor2.getClass());
    }
}
