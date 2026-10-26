package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmployeeCreateDTO {

    @NotNull(message = "新增标记不能为空")
    @Min(value = 0, message = "新增时id只能为0")
    @Max(value = 0, message = "新增时id只能为0")
    private Long id;

    @NotBlank(message = "登录账号不能为空")
    @Size(max = 30, message = "登录账号不能超过30个字符")
    private String username;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 30, message = "姓名不能超过30个字符")
    private String name;

    @NotBlank(message = "手机号不能为空")
    @Pattern(
            regexp = "^1\\d{10}$",
            message = "手机号必须为11位大陆手机号"
    )
    private String phone;

    @NotNull(message = "账号状态不能为空")
    @Min(value = 0, message = "账号状态只能为0或1")
    @Max(value = 1, message = "账号状态只能为0或1")
    private Integer status;

    @NotBlank(message = "初始密码不能为空")
    @Size(
            min = 8,
            max = 64,
            message = "初始密码长度必须为8到64个字符"
    )
    private String initialPassword;

    // Jackson赋值时先规范化，再执行@Valid校验。
    public void setUsername(String username) {
        this.username = username == null ? null : username.trim();
    }

    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }

    // 密码不trim，保留用户实际输入。
    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("新增请求包含不允许的字段");
    }
}