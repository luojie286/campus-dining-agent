package com.sky.service.impl;

import com.sky.dto.ShopUpdateDTO;
import com.sky.entity.Shop;
import com.sky.exception.NotFoundException;
import com.sky.mapper.ShopMapper;
import com.sky.service.EmployeePermissionService;
import com.sky.service.ShopService;
import com.sky.vo.ShopVO;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!bootstrap")
public class ShopServiceImpl implements ShopService {

    private static final long SHOP_ID = 1L;
    private static final int CLOSED = 0;
    private static final int OPEN = 1;

    private final ShopMapper shopMapper;
    private final EmployeePermissionService permissionService;

    public ShopServiceImpl(
            ShopMapper shopMapper,
            EmployeePermissionService permissionService) {
        this.shopMapper = shopMapper;
        this.permissionService = permissionService;
    }

    @Override
    public ShopVO getForAdmin() {
        permissionService.requireOperationsAccess();
        return getShop();
    }

    // 点餐端身份由 JwtTokenUserInterceptor 校验。
    @Override
    public ShopVO getForUser() {
        return getShop();
    }

    private ShopVO getShop() {
        Shop shop = requireShop(shopMapper.selectById(SHOP_ID));
        return toVO(shop, isOpen(shop));
    }

    @Override
    @Transactional
    public ShopVO updateStatus(ShopUpdateDTO dto) {
        permissionService.requireOperationsAccess();

        // 在同一事务中完成读取、比较和写入，避免两个运营请求并发判断旧值。
        Shop shop = requireShop(shopMapper.selectForUpdate(SHOP_ID));
        boolean currentOpen = isOpen(shop);
        boolean targetOpen = dto.getOpen();

        if (currentOpen == targetOpen) {
            return toVO(shop, currentOpen);
        }

        int targetStatus = targetOpen ? OPEN : CLOSED;
        int updatedRows = shopMapper.updateStatus(SHOP_ID, targetStatus);

        if (updatedRows != 1) {
            throw new IllegalStateException("更新店铺营业状态失败");
        }

        return toVO(shop, targetOpen);
    }

    private Shop requireShop(Shop shop) {
        if (shop == null) {
            throw new NotFoundException("店铺设置不存在，请先初始化");
        }
        return shop;
    }

    private boolean isOpen(Shop shop) {
        Integer status = shop.getStatus();

        if (Integer.valueOf(OPEN).equals(status)) {
            return true;
        }
        if (Integer.valueOf(CLOSED).equals(status)) {
            return false;
        }

        throw new IllegalStateException("数据库中的店铺营业状态不合法");
    }

    private ShopVO toVO(Shop shop, boolean open) {
        return new ShopVO(
                shop.getName(),
                open,
                shop.getOpeningHours(),
                shop.getPickupPoint()
        );
    }
}