package com.xw.tmp.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xw.tmp.demo.entity.Order;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 接口级熔断配置：所有 SELECT 超时阈值 3 秒（与 yml 全局一致，此处仅做演示覆盖）
 */
//@SqlCircuitBreaker(timeoutMs = 3000)
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 正常查询：查询指定用户的订单列表
     */
    @Select("SELECT * FROM t_order WHERE user_id = #{userId}")
    List<Order> selectByUserId(@Param("userId") Long userId);

    /**
     * 模拟慢查询：通过 MySQL SLEEP 函数让 SQL 执行指定秒数，触发超时熔断。
     * 使用 ${} 直接替换，避免 SLEEP 参数被 PreparedStatement 绑定失败。
     */
    @Select("SELECT SLEEP(${seconds})")
    Integer simulateSlowQuery(@Param("seconds") int seconds);

    /**
     * 模拟 SQL 异常：查询一张压根不存在的表。
     * 拦截器会直接透传 BadSqlGrammarException，不触发熔断、不记录熔断计数。
     */
    @Select("SELECT * FROM t_non_exist_table WHERE id = #{id}")
    List<Order> selectFromNonExistTable(@Param("id") Long id);

    /**
     * 方法级覆盖：该方法单独设置更长超时（演示方法注解优先于接口注解）
     */

    @Select("SELECT * FROM t_order WHERE status = #{status}")
    List<Order> selectByStatus(@Param("status") Integer status);

    /**
     * 分页查询（带可选状态过滤）。
     * MP 会基于此 @Select 自动生成两条 SQL：
     * 1. SELECT COUNT(*) FROM t_order WHERE status = ?   ← COUNT 查询，独立熔断 Key
     * 2. SELECT * FROM t_order WHERE status = ? LIMIT ?,? ← 数据查询，独立熔断 Key
     * 两条 SQL 各自独立计入超时次数，互不干扰。
     * 方法级注解将 SELECT 超时放宽为 5s（接口级默认 3s），演示注解优先级。
     */
    @Select("SELECT * FROM t_order WHERE (#{status} IS NULL OR status = #{status}) ORDER BY create_time DESC")
    //@SqlCircuitBreaker(timeoutMs = 1, failureThreshold = 1, circuitOpenMs = 30000, disableCircuitBreaker = false)
    IPage<Order> selectPageByStatus(IPage<Order> page, @Param("status") Integer status);
}
