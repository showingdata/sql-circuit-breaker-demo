package com.xw.tmp.demo.config;

import io.github.showingdata.starter.framework.circuitbreaker.CircuitBreakerEvent;
import io.github.showingdata.starter.framework.circuitbreaker.message.MessageCenterClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 消息中心占位实现（仅用于 Demo，真实接入时替换为消息中心 starter 提供的 Bean）
 */
@Slf4j
@Component
public class MockMessageCenterClient implements MessageCenterClient {

    @Override
    public void send(CircuitBreakerEvent event) {
        log.warn("===================================================");
        log.warn("  [消息中心] 收到熔断事件通知");
        log.warn("  应用名    : {}", event.getApplicationName());
        log.warn("  事件类型  : {}", event.getEventType());
        log.warn("  Mapper    : {}", event.getMapperId());
        log.warn("  SQL 指纹  : {}", event.getSqlFingerprint());
        log.warn("  SQL 类型  : {}", event.getSqlType());
        log.warn("  执行耗时  : {} ms", event.getCost());
        log.warn("  超时阈值  : {} ms", event.getTimeoutThreshold());
        log.warn("  熔断时长  : {} ms", event.getCircuitOpenMs());
        log.warn("===================================================");
    }
}
