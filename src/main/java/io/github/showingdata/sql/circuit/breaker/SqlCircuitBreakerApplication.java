package io.github.showingdata.sql.circuit.breaker;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
@MapperScan("io.github.showingdata.sql.circuit.breaker.mapper")
public class SqlCircuitBreakerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SqlCircuitBreakerApplication.class, args);
        log.info("启动完成");
    }
}
