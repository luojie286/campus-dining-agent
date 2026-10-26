package com.sky.controller.admin;

import com.sky.dto.EmployeeLoginDTO;
import com.sky.entity.Employee;
import com.sky.result.Result;
import com.sky.service.EmployeeService;
import com.sky.util.JwtUtil;
import com.sky.vo.EmployeeLoginVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.sky.context.BaseContext;
import org.springframework.web.bind.annotation.GetMapping;
import com.sky.vo.EmployeeVO;
import com.sky.dto.EmployeeCreateDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.result.PageResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import com.sky.dto.EmployeeStatusUpdateDTO;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import com.sky.dto.EmployeeUpdateDTO;

import java.util.List;

@RestController
@RequestMapping("/api/admin/employee")

public class EmployeeController {

    private final EmployeeService employeeService;
    private final JwtUtil jwtUtil;

    public EmployeeController(
            EmployeeService employeeService,
            JwtUtil jwtUtil) {
        this.employeeService = employeeService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/{id}")
    public Result<EmployeeVO> getById(
            @PathVariable("id") Long id) {

        EmployeeVO employeeVO = employeeService.getById(id);

        return Result.success(employeeVO);
    }

    @PutMapping
    public Result<EmployeeVO> update(
            @Valid @RequestBody EmployeeUpdateDTO dto) {

        EmployeeVO employeeVO = employeeService.update(dto);

        return Result.success(employeeVO);
    }

    @PutMapping("/{id}/status")
    public Result<EmployeeVO> updateStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody EmployeeStatusUpdateDTO dto) {

        EmployeeVO employeeVO =
                employeeService.updateStatus(id, dto);

        return Result.success(employeeVO);
    }

    @GetMapping("/page")
    public Result<PageResult<EmployeeVO>> page(
            @Valid @ModelAttribute EmployeePageQueryDTO dto) {

        PageResult<EmployeeVO> pageResult =
                employeeService.pageQuery(dto);

        return Result.success(pageResult);
    }

    @PostMapping
    public Result<EmployeeVO> create(
            @Valid @RequestBody EmployeeCreateDTO dto) {

        EmployeeVO employeeVO = employeeService.create(dto);

        return Result.success(employeeVO);
    }

    @PostMapping("/login")
    public Result<EmployeeLoginVO> login(
            @Valid @RequestBody EmployeeLoginDTO loginDTO) {

        Employee employee = employeeService.login(loginDTO);

        String token = jwtUtil.createToken(
                employee.getId(),
                "admin"
        );
        
        EmployeeLoginVO loginVO = EmployeeLoginVO.builder()
                .id(employee.getId())
                .name(employee.getName())
                .role("admin")
                .accountRole(employee.getAccountRole())
                .token(token)
                .build();

        return Result.success(loginVO);
    }

    @GetMapping("/current")
    public Result<Long> current() {
        return Result.success(BaseContext.getCurrentId());
    }

    @GetMapping("/list")
    public Result<List<EmployeeVO>> list() {
        return Result.success(employeeService.listOperators());
    }
}