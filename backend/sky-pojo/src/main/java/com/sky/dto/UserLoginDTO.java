package com.sky.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserLoginDTO {
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50,message = "用户名不能超过50个字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(max = 100,message = "密码不超过100个字符")
    private String password;
}
