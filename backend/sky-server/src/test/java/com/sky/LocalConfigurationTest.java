package com.sky;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/** 只验证新配置前缀和环境变量绑定，不连接 Redis 或创建业务数据。 */
class LocalConfigurationTest {
    @Test void localRedisConfigurationUsesBoot3Properties() throws Exception {
        var environment = new MockEnvironment()
            .withProperty("REDIS_HOST", "redis.example.invalid")
            .withProperty("REDIS_PORT", "6381");
        var sources = new YamlPropertySourceLoader()
            .load("local", new ClassPathResource("application-local.yml"));
        sources.forEach(source -> environment.getPropertySources().addLast(source));

        var redis = Binder.get(environment)
            .bind("spring.data.redis", Bindable.of(RedisProperties.class)).get();
        assertThat(redis.getHost()).isEqualTo("redis.example.invalid");
        assertThat(redis.getPort()).isEqualTo(6381);
    }
}
