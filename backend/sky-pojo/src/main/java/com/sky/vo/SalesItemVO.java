package com.sky.vo;

import java.math.BigDecimal;

/** 销量项：id 是商品对外 itemId（菜品 id*2，套餐 id*2+1）。 */
public record SalesItemVO(long id, String name, int quantity, BigDecimal amount) {
}
