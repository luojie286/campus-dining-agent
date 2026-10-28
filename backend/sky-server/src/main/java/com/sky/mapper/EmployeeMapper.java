package com.sky.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.sky.annotation.AutoFill;
import com.sky.entity.Employee;
import com.sky.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {

    @Override
    @AutoFill(OperationType.INSERT)
    int insert(Employee employee);

    @Override
    @AutoFill(OperationType.UPDATE)
    int update(
            @Param(Constants.ENTITY) Employee employee,
            @Param(Constants.WRAPPER) Wrapper<Employee> updateWrapper
    );
}