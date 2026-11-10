package com.sky;

import com.sky.cache.MenuPageCache;
import com.sky.dto.DishPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.vo.MenuItemVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "RUN_CACHE_REDIS_TEST", matches = "true")
class MenuCacheRedisTest {
    @Autowired private MenuPageCache cache;
    @Autowired private StringRedisTemplate redis;
    @Autowired private PlatformTransactionManager transactions;
    @Value("${sky.menu-cache.prefix}") private String prefix;

    @Test
    void redisPageSurvivesRollbackAndCommitSwitchesNamespace() {
        String version = cache.currentVersion();
        assertThat(version).isNotBlank();
        DishPageQueryDTO query = new DishPageQueryDTO();
        query.setPage(4);
        String oldKey = cache.pageKey(version, query);
        PageResult<MenuItemVO> emptyPage = new PageResult<>(20, List.of());
        try {
            cache.write(oldKey, emptyPage);
            assertThat(cache.read(oldKey)).isEqualTo(emptyPage);
            var tx = new TransactionTemplate(transactions);
            tx.executeWithoutResult(status -> {
                cache.invalidateAfterCommit();
                status.setRollbackOnly();
            });
            assertThat(cache.currentVersion()).isEqualTo(version);

            tx.executeWithoutResult(status -> cache.invalidateAfterCommit());
            String next = cache.currentVersion();
            assertThat(next).isNotBlank().isNotEqualTo(version);
            assertThat(cache.read(cache.pageKey(next, query))).isNull();
        } finally {
            redis.delete(List.of(prefix + ":version", oldKey));
        }
    }
}
