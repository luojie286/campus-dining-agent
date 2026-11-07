package com.sky.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

public record MenuItemVO(
        Long id,
        String kind,
        Long categoryId,
        String name,
        BigDecimal price,
        String description,
        Integer status,
        Integer stock,
        List<String> tags,
        String art,
        @JsonInclude(JsonInclude.Include.NON_NULL) String image,
        List<Composition> composition,
        int availableStock,
        List<CompositionItem> compositionItems
) {
    public record Composition(Long dishId, int quantity) {}

    public record CompositionItem(Long dishId, int quantity, String name) {}
}