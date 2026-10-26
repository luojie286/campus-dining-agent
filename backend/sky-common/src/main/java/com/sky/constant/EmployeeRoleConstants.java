package com.sky.constant;

public final class EmployeeRoleConstants {

    // 可以管理普通运营账号。
    public static final String ACCOUNT_ADMIN = "ACCOUNT_ADMIN";

    // 可以使用餐饮运营功能，不能管理运营账号。
    public static final String OPERATOR = "OPERATOR";

    private EmployeeRoleConstants() {
    }
}