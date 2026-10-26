package com.sky.service;

import com.sky.constant.EmployeeRoleConstants;
import com.sky.context.BaseContext;
import com.sky.entity.Employee;
import com.sky.exception.ForbiddenException;
import com.sky.mapper.EmployeeMapper;
import org.springframework.stereotype.Service;


@Service
public class EmployeePermissionService {

    private final EmployeeMapper employeeMapper;

    public EmployeePermissionService(EmployeeMapper employeeMapper) {
        this.employeeMapper = employeeMapper;
    }

    public void requireAccountManagement() {
        Long currentId = BaseContext.getCurrentId();

        if(currentId == null){
            throw new ForbiddenException("当前身份无权登录管理运营账号");
        }

        Employee current = employeeMapper.selectById(currentId);

        if(current == null){
            throw new ForbiddenException("当前运营账号不存在");
        }

        if (!Integer.valueOf(1).equals(current.getStatus())) {
            throw new ForbiddenException("当前运营账号已停用");
        }

        if (!EmployeeRoleConstants.ACCOUNT_ADMIN.equals(
                current.getAccountRole())) {
            throw new ForbiddenException("当前账号没有运营账号管理权限");
        }
    }

    public void requireOperationsAccess() {
        Long currentId = BaseContext.getCurrentId();

        if (currentId == null) {
            throw new ForbiddenException("当前身份无权操作");
        }

        Employee current = employeeMapper.selectById(currentId);

        if (current == null) {
            throw new ForbiddenException("当前运营账号不存在");
        }

        if (!Integer.valueOf(1).equals(current.getStatus())) {
            throw new ForbiddenException("当前运营账号已停用");
        }

        String role = current.getAccountRole();

        boolean allowed =
                EmployeeRoleConstants.ACCOUNT_ADMIN.equals(role)
                        || EmployeeRoleConstants.OPERATOR.equals(role);

        if (!allowed) {
            throw new ForbiddenException("当前账号没有餐饮运营权限");
        }
    }

}