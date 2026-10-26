package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class EmployeeUpdateDTO {

    @NotNull(message = "运营账号ID不能为空")
    @Min(value = 1, message = "运营账号ID必须大于0")
    @Max(
            value = 9007199254740991L,
            message = "运营账号ID超出允许范围"
    )
    private Long id;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 30, message = "姓名不能超过30个字符")
    private String name;

    @NotBlank(message = "手机号不能为空")
    @Pattern(
            regexp = "^1\\d{10}$",
            message = "手机号必须为11位大陆手机号"
    )
    private String phone;

    // 兼容旧表单：允许传入，但不允许修改。
    @Size(
            min = 1,
            max = 30,
            message = "登录账号长度必须为1到30个字符"
    )
    private String username;

    // 兼容旧表单：省略时保留原状态，没有默认值。
    @Min(value = 0, message = "账号状态只能为0或1")
    @Max(value = 1, message = "账号状态只能为0或1")
    private Integer status;

    @JsonSetter("id")
    public void setId(Object value) {
        if (value == null) {
            this.id = null;
            return;
        }

        if (!(value instanceof Integer)
                && !(value instanceof Long)) {
            throw new IllegalArgumentException(
                    "运营账号ID必须是整数"
            );
        }

        this.id = ((Number) value).longValue();
    }

    @JsonSetter("name")
    public void setName(Object value) {
        String text = readString(value, "姓名");
        this.name = text == null ? null : text.trim();
    }

    @JsonSetter("phone")
    public void setPhone(Object value) {
        this.phone = readString(value, "手机号");
    }

    @JsonSetter(value = "username", nulls = Nulls.FAIL)
    public void setUsername(Object value) {
        this.username = readString(value, "登录账号").trim();
    }

    @JsonSetter(value = "status", nulls = Nulls.FAIL)
    public void setStatus(Object value) {
        if (!(value instanceof Integer integer)) {
            throw new IllegalArgumentException(
                    "账号状态必须是整数0或1"
            );
        }

        this.status = integer;
    }

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException(
                "编辑请求包含不允许的字段"
        );
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