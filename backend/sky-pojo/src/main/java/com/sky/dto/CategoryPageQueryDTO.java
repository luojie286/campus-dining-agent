package com.sky.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryPageQueryDTO {

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码不能小于1")
    @Max(value = 1000000, message = "页码不能超过1000000")
    private Integer page = 1;

    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能超过100")
    private Integer pageSize = 8;

    @Size(max = 100, message = "搜索关键词不能超过100个字符")
    private String name;

    @Min(value = 1, message = "分类类型只能为1或2")
    @Max(value = 2, message = "分类类型只能为1或2")
    private Integer type;

    @Min(value = 0, message = "分类状态只能为0或1")
    @Max(value = 1, message = "分类状态只能为0或1")
    private Integer status;

    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }
}