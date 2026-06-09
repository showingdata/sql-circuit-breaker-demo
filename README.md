SQL 超时熔断 SDK 测试项目

## Spring Boot 指标监控

当前 demo 已接入 Actuator + Micrometer Prometheus。

访问地址：

```text
http://localhost:8089/actuator/prometheus
```

本地验证链路：

```text
Spring Boot /actuator/prometheus
  -> Metricbeat 7.10.2 Prometheus module
  -> Elasticsearch 7.10.2
  -> Kibana 7.10.2
```

当前只保留这些应用级基础指标：

| 类别 | Prometheus 指标前缀 |
|---|---|
| JVM 内存 | `jvm_memory_*` |
| JVM Buffer | `jvm_buffer_*` |
| 磁盘 | `disk_*` |
| 进程 | `process_*` |
| 系统 | `system_*` |
| 线程池 | `executor_*` |
| HTTP 请求 | `http_server_requests_*` |

Metricbeat 7.10.2 写入 ES 后，Kibana Discover 常用字段：

```text
prometheus.jvm_memory_used_bytes.value
prometheus.jvm_buffer_memory_used_bytes.value
prometheus.disk_free_bytes.value
prometheus.process_cpu_usage.value
prometheus.system_cpu_usage.value
prometheus.executor_active_threads.value
prometheus.http_server_requests_seconds.histogram
```

Discover 建议过滤：

```text
not error.message:*
```

完整 Windows / K8s 落地文档见：

```text
D:\projects\jvm-monitoring-quick-landing
```
