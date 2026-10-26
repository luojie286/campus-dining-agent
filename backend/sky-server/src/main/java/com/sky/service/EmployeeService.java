package com.sky.service;

import com.sky.dto.EmployeeLoginDTO;
import com.sky.entity.Employee;
import com.sky.vo.EmployeeVO;
import com.sky.dto.EmployeeCreateDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.result.PageResult;
import com.sky.dto.EmployeeStatusUpdateDTO;
import com.sky.dto.EmployeeUpdateDTO;

import java.util.List;

public interface EmployeeService {

    Employee login(EmployeeLoginDTO loginDTO);

    List<EmployeeVO> listOperators();

    EmployeeVO create(EmployeeCreateDTO dto);

    PageResult<EmployeeVO> pageQuery(EmployeePageQueryDTO dto);

    EmployeeVO updateStatus(Long id, EmployeeStatusUpdateDTO dto);

    EmployeeVO getById(Long id);

    EmployeeVO update(EmployeeUpdateDTO dto);
}