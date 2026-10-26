package com.sky.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfiguration {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor =
                new MybatisPlusInterceptor();

        PaginationInnerInterceptor pagination =
                new PaginationInnerInterceptor(DbType.MYSQL);

        // 单页最多100条，作为额外保护。
        pagination.setMaxLimit(100L);

        // 超过最后一页时返回空记录，不跳回第一页。
        pagination.setOverflow(false);

        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }
}