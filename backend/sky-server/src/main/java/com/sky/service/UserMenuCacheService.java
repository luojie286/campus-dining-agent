package com.sky.service;

import com.sky.cache.MenuPageCache;
import com.sky.dto.DishPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.vo.MenuItemVO;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Profile("!bootstrap")
public class UserMenuCacheService {

    private final MenuService menuService;
    private final MenuPageCache menuPageCache;

    public UserMenuCacheService(
            MenuService menuService,
            MenuPageCache menuPageCache) {

        this.menuService = menuService;
        this.menuPageCache = menuPageCache;
    }

    public PageResult<MenuItemVO> pageForUser(DishPageQueryDTO query) {
        String version = menuPageCache.currentVersion();
        if (version == null) {
            return menuService.pageForUser(query);
        }

        String key = menuPageCache.pageKey(version, query);
        PageResult<MenuItemVO> cached = menuPageCache.read(key);
        if (cached != null) {
            return cached;
        }

        String owner = UUID.randomUUID().toString();
        Boolean locked = menuPageCache.tryLock(key, owner);

        if (locked == null) {
            return menuService.pageForUser(query);
        }

        if (!locked) {
            return waitForCacheOrQueryDatabase(key, query);
        }

        try {
            // 取得锁之前，另一个请求可能已经完成回填。
            cached = menuPageCache.read(key);
            if (cached != null) {
                return cached;
            }

            PageResult<MenuItemVO> result = menuService.pageForUser(query);

            // 固定写入进入查询时的版本。
            // 即使期间运营修改了菜品，也不会覆盖新版本的数据。
            menuPageCache.write(key, result);
            return result;
        } finally {
            menuPageCache.unlock(key, owner);
        }
    }

    private PageResult<MenuItemVO> waitForCacheOrQueryDatabase(
            String key, DishPageQueryDTO query) {

        // 短暂等待其他请求回填，超过等待次数后直接查库。
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                Thread.sleep(40);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }

            PageResult<MenuItemVO> cached = menuPageCache.read(key);
            if (cached != null) {
                return cached;
            }
        }

        // 当前请求没有回填资格，只返回数据库结果。
        return menuService.pageForUser(query);
    }
}