package com.sky.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmployeeVO {

    private Long id;

    private String username;

    private String name;

    private String phone;

    private Integer status;

    private String accountRole;
}