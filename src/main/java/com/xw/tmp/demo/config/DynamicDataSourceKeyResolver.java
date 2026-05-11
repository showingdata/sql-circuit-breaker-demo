package com.xw.tmp.demo.config;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import io.github.showingdata.starter.framework.circuitbreaker.datasource.DataSourceKeyResolver;
import org.apache.ibatis.mapping.MappedStatement;
import org.springframework.stereotype.Component;

/**
 * @author chenjiang
 * @date 2026/5/7 10:27
 * @package com.xw.tmp.demo.config
 * @className DynamicDataSourceKeyResolver
 * @description
 */
@Component
public class DynamicDataSourceKeyResolver implements DataSourceKeyResolver {
    public String resolve(MappedStatement ms) {
        // dynamic-datasource 通过 ThreadLocal 记录当前数据源 key
        String dsKey = DynamicDataSourceContextHolder.peek();
        return dsKey != null ? dsKey : "master";
    }
}
