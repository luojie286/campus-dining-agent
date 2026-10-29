package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.sky.json.MoneyDeserializer;
import jakarta.validation.constraints.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
public class DishUpdateDTO {

    @NotNull(message = "菜品ID不能为空")
    @Min(value = 2, message = "菜品ID不合法")
    @Max(value = 9007199254740991L, message = "菜品ID超出允许范围")
    private Long id;

    @NotNull(message = "商品类型不能为空")
    @Pattern(regexp = "dish", message = "商品类型必须为dish")
    private String kind;

    @NotBlank(message = "菜品名称不能为空")
    @Size(max = 50, message = "菜品名称不能超过50个字符")
    private String name;

    @NotNull(message = "分类不能为空")
    @Min(value = 1, message = "分类ID必须大于0")
    @Max(
            value = 9007199254740991L,
            message = "分类ID超出允许范围"
    )
    private Long categoryId;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格不能小于0.01元")
    @DecimalMax(value = "9999", message = "价格不能超过9999元")
    @JsonDeserialize(using = MoneyDeserializer.class)
    private BigDecimal price;

    @NotNull(message = "描述不能为null")
    @Size(max = 300, message = "描述不能超过300个字符")
    private String description;

    @NotNull(message = "售卖状态不能为空")
    @Min(value = 0, message = "售卖状态只能为0或1")
    @Max(value = 1, message = "售卖状态只能为0或1")
    private Integer status;

    // 兼容旧表单，但普通编辑不使用此字段更新库存。
    @Min(value = 0, message = "初始库存不能小于0")
    private Integer stock;

    @NotNull(message = "标签数组不能为空")
    @Size(max = 6, message = "标签最多6个")
    private List<
            @NotNull
            @Pattern(
                    regexp = "不辣|微辣|辣|清淡|素食|高蛋白",
                    message = "标签不在允许范围内"
            )
                    String> tags;

    @NotNull(message = "插画类型不能为空")
    @Pattern(
            regexp = "chicken|fish|beef|salad|noodle|tofu",
            message = "插画类型不合法"
    )
    private String art;

    // 菜品没有组成明细；接收该字段只是保持商品接口结构兼容。
    @NotNull(message = "composition必须为空数组")
    @Size(max = 0, message = "菜品不能包含组成明细")
    private List<Object> composition;

    @Size(max = 255, message = "图片地址不能超过255个字符")
    private String image;

    @JsonSetter("id")
    public void setId(Object value) {
        this.id = readLong(value, "id");
    }

    @JsonSetter("categoryId")
    public void setCategoryId(Object value) {
        this.categoryId = readLong(value, "categoryId");
    }

    @JsonSetter("kind")
    public void setKind(Object value) {
        this.kind = readString(value, "kind");
    }

    @JsonSetter("name")
    public void setName(Object value) {
        String text = readString(value, "name");
        this.name = text == null ? null : text.trim();
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    @JsonSetter("description")
    public void setDescription(Object value) {
        this.description = readString(value, "description");
    }

    @JsonSetter("status")
    public void setStatus(Object value) {
        this.status = readInteger(value, "status");
    }

    @JsonSetter("stock")
    public void setStock(Object value) {
        this.stock = readInteger(value, "stock");
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    @JsonSetter("art")
    public void setArt(Object value) {
        this.art = readString(value, "art");
    }

    public void setComposition(List<Object> composition) {
        this.composition = composition;
    }

    // image可以省略，但显式传null不符合当前OpenAPI的字段类型。
    @JsonSetter(value = "image", nulls = Nulls.FAIL)
    public void setImage(Object value) {
        this.image = readString(value, "image");
    }

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("请求包含不允许的字段");
    }

    private Long readLong(Object value, String fieldName) {
        if (value == null) {
            return null;
        }

        if (!(value instanceof Integer)
                && !(value instanceof Long)) {
            throw new IllegalArgumentException(
                    fieldName + "必须是整数"
            );
        }

        return ((Number) value).longValue();
    }

    private Integer readInteger(Object value, String fieldName) {
        if (value == null) {
            return null;
        }

        if (!(value instanceof Integer integer)) {
            throw new IllegalArgumentException(
                    fieldName + "必须是有效整数"
            );
        }

        return integer;
    }

    private String readString(Object value, String fieldName) {
        if (value == null) {
            return null;
        }

        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(
                    fieldName + "必须是字符串"
            );
        }

        return text;
    }
}
