package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class CategorySaveDTO {

    @NotNull(message = "分类ID不能为空")
    @Min(value = 0, message = "分类ID不能小于0")
    @Max(value = 9007199254740991L, message = "分类ID超出允许范围")
    private Long id;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 30, message = "分类名称不能超过30个字符")
    private String name;

    @NotNull(message = "分类类型不能为空")
    @Min(value = 1, message = "分类类型只能为1或2")
    @Max(value = 2, message = "分类类型只能为1或2")
    private Integer type;

    @NotNull(message = "排序值不能为空")
    @Min(value = 0, message = "排序值不能小于0")
    private Integer sort;

    @NotNull(message = "分类状态不能为空")
    @Min(value = 0, message = "分类状态只能为0或1")
    @Max(value = 1, message = "分类状态只能为0或1")
    private Integer status;

    @JsonSetter("id")
    public void setId(Object value) {
        if (value == null) {
            this.id = null;
            return;
        }

        if (!(value instanceof Integer)
                && !(value instanceof Long)) {
            throw new IllegalArgumentException("分类ID必须是整数");
        }

        this.id = ((Number) value).longValue();
    }

    @JsonSetter("name")
    public void setName(Object value) {
        if (value == null) {
            this.name = null;
            return;
        }

        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("分类名称必须是字符串");
        }

        this.name = text.trim();
    }

    @JsonSetter("type")
    public void setType(Object value) {
        this.type = readInteger(value, "分类类型");
    }

    @JsonSetter("sort")
    public void setSort(Object value) {
        this.sort = readInteger(value, "排序值");
    }

    @JsonSetter("status")
    public void setStatus(Object value) {
        this.status = readInteger(value, "分类状态");
    }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("请求包含不允许的字段");
    }

    private Integer readInteger(Object value, String fieldName) {
        if (value == null) {
            return null;
        }

        if (!(value instanceof Integer integer)) {
            throw new IllegalArgumentException(
                    fieldName + "必须是有效的整数"
            );
        }

        return integer;
    }
}