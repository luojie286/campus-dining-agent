package com.sky;

import com.sky.cache.MenuPageCache;
import com.sky.dto.DishPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.service.MenuService;
import com.sky.service.UserMenuCacheService;
import com.sky.vo.MenuItemVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MenuCacheServiceTest {

    private final MenuService database = mock(MenuService.class);
    private final MenuPageCache cache = mock(MenuPageCache.class);
    private final UserMenuCacheService service = new UserMenuCacheService(database, cache);

    @Test
    void combinedMenuHitPreservesTotalWithoutQueryingDatabase() {
        DishPageQueryDTO query = new DishPageQueryDTO();
        query.setPage(3);
        PageResult<MenuItemVO> lastPage = new PageResult<>(16, List.of());
        when(cache.currentVersion()).thenReturn("version");
        when(cache.pageKey("version", query)).thenReturn("page");
        when(cache.read("page")).thenReturn(lastPage);

        assertThat(service.pageForUser(query)).isEqualTo(lastPage);
        verifyNoInteractions(database);
    }

    @Test
    void cacheMissReusesDatabaseResultAndReleasesOwnedLock() {
        DishPageQueryDTO query = new DishPageQueryDTO();
        query.setCategoryId(7L);
        PageResult<MenuItemVO> result = new PageResult<>(0, List.of());
        when(cache.currentVersion()).thenReturn("version");
        when(cache.pageKey("version", query)).thenReturn("page");
        when(cache.tryLock(eq("page"), anyString())).thenReturn(true);
        when(database.pageForUser(query)).thenReturn(result);

        assertThat(service.pageForUser(query)).isEqualTo(result);
        verify(cache).write("page", result);
        verify(cache).unlock(eq("page"), anyString());
    }

    @Test
    void missingRedisVersionFallsBackWithoutWritingCache() {
        DishPageQueryDTO query = new DishPageQueryDTO();
        PageResult<MenuItemVO> result = new PageResult<>(0, List.of());
        when(database.pageForUser(query)).thenReturn(result);

        assertThat(service.pageForUser(query)).isEqualTo(result);
        verify(cache).currentVersion();
        verifyNoMoreInteractions(cache);
    }
}
