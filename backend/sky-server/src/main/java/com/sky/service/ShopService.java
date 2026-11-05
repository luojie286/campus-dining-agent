package com.sky.service;

import com.sky.dto.ShopUpdateDTO;
import com.sky.vo.ShopVO;

public interface ShopService {

    ShopVO getForAdmin();

    ShopVO getForUser();

    ShopVO updateStatus(ShopUpdateDTO dto);
}
