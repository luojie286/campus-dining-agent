package com.sky.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sky.constant.EmployeeRoleConstants;
import com.sky.context.BaseContext;
import com.sky.dto.EmployeeCreateDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.entity.Employee;
import com.sky.exception.BadRequestException;
import com.sky.exception.BusinessException;
import com.sky.exception.ConflictException;
import com.sky.mapper.EmployeeMapper;
import com.sky.service.EmployeePermissionService;
import com.sky.service.EmployeeService;
import com.sky.vo.EmployeeVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.result.PageResult;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sky.dto.EmployeeStatusUpdateDTO;
import com.sky.exception.ForbiddenException;
import com.sky.exception.NotFoundException;
import org.springframework.transaction.annotation.Transactional;
import com.sky.dto.EmployeeUpdateDTO;
import java.util.Objects;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeMapper employeeMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmployeePermissionService employeePermissionService;

    public EmployeeServiceImpl(
            EmployeeMapper employeeMapper,
            PasswordEncoder passwordEncoder,
            EmployeePermissionService employeePermissionService) {
        this.employeeMapper = employeeMapper;
        this.passwordEncoder = passwordEncoder;
        this.employeePermissionService = employeePermissionService;
    }

    @Override
    public EmployeeVO getById(Long id) {
        // 1. 检查当前操作人的账号管理权限。
        employeePermissionService.requireAccountManagement();

        // 2. 查询目标，详情查询不加行锁。
        Employee target = findOperator(id, false);

        // 3. 返回安全资料，不返回密码。
        return toEmployeeVO(target);
    }

    @Override
    @Transactional
    public EmployeeVO update(EmployeeUpdateDTO dto) {
        // 1. 检查当前操作人权限。
        employeePermissionService.requireAccountManagement();

        // 2. 查询并锁定目标账号。
        Employee target = findOperator(dto.getId(), true);

        // 3. 兼容旧表单，但禁止修改登录账号。
        if (dto.getUsername() != null
                && !dto.getUsername().equals(target.getUsername())) {
            throw new BadRequestException("登录账号不允许修改");
        }

        // 4. 判断本次是否确实修改了资料或兼容状态字段。
        boolean nameChanged =
                !Objects.equals(dto.getName(), target.getName());

        boolean phoneChanged =
                !Objects.equals(dto.getPhone(), target.getPhone());

        boolean statusChanged =
                dto.getStatus() != null
                        && !Objects.equals(
                        dto.getStatus(),
                        target.getStatus()
                );

        // 相同资料重复保存，直接成功，不重复更新审计字段。
        if (!nameChanged && !phoneChanged && !statusChanged) {
            return toEmployeeVO(target);
        }

        // 5. 构建明确的字段更新。
        LocalDateTime now =
                LocalDateTime.now(ZoneId.of("Asia/Shanghai"));

        LambdaUpdateWrapper<Employee> updateWrapper =
                new LambdaUpdateWrapper<Employee>()
                        .eq(Employee::getId, target.getId())
                        .eq(
                                Employee::getAccountRole,
                                EmployeeRoleConstants.OPERATOR
                        )
                        .set(Employee::getName, dto.getName())
                        .set(Employee::getPhone, dto.getPhone())
                        .set(Employee::getUpdateTime, now)
                        .set(
                                Employee::getUpdateUser,
                                BaseContext.getCurrentId()
                        );

        // 只有请求实际包含status，才允许更新状态。
        if (dto.getStatus() != null) {
            updateWrapper.set(
                    Employee::getStatus,
                    dto.getStatus()
            );
        }

        // 6. 写入数据库。
        int rows = employeeMapper.update(null, updateWrapper);

        if (rows != 1) {
            throw new IllegalStateException("编辑运营账号失败");
        }

        // 7. 更新内存中的目标对象，构造本次响应。
        target.setName(dto.getName());
        target.setPhone(dto.getPhone());

        if (dto.getStatus() != null) {
            target.setStatus(dto.getStatus());
        }

        return toEmployeeVO(target);
    }

    /**
     * 查询目标普通运营账号。
     *
     * lock=true时使用行锁，必须在事务内调用。
     */
    private Employee findOperator(Long id, boolean lock) {
        if (id == null || id <= 0 || id > 9007199254740991L) {
            throw new BadRequestException("运营账号ID不合法");
        }

        LambdaQueryWrapper<Employee> query =
                new LambdaQueryWrapper<Employee>()
                        .select(
                                Employee::getId,
                                Employee::getUsername,
                                Employee::getName,
                                Employee::getPhone,
                                Employee::getStatus,
                                Employee::getAccountRole
                        )
                        .eq(Employee::getId, id);

        if (lock) {
            query.last("FOR UPDATE");
        }

        Employee target = employeeMapper.selectOne(query);

        if (target == null) {
            throw new NotFoundException("运营账号不存在");
        }

        if (!EmployeeRoleConstants.OPERATOR.equals(
                target.getAccountRole())) {
            throw new ForbiddenException(
                    "只能查询和编辑普通运营账号"
            );
        }

        return target;
    }

    @Override
    @Transactional
    public EmployeeVO updateStatus(
            Long id,
            EmployeeStatusUpdateDTO dto) {

        // 1. 检查当前操作人是否为启用的账号管理员。
        employeePermissionService.requireAccountManagement();

        // 2. 检查目标ID，与接口合同的安全整数范围保持一致。
        if (id == null || id <= 0 || id > 9007199254740991L) {
            throw new BadRequestException("运营账号ID不合法");
        }

        // 3. 查询并锁定目标账号。
        Employee target = employeeMapper.selectOne(
                new LambdaQueryWrapper<Employee>()
                        .select(
                                Employee::getId,
                                Employee::getUsername,
                                Employee::getName,
                                Employee::getPhone,
                                Employee::getStatus,
                                Employee::getAccountRole
                        )
                        .eq(Employee::getId, id)
                        .last("FOR UPDATE")
        );

        // 4. 目标不存在，返回404。
        if (target == null) {
            throw new NotFoundException("运营账号不存在");
        }

        // 5. 本接口只能管理普通运营，不能操作管理员。
        if (!EmployeeRoleConstants.OPERATOR.equals(
                target.getAccountRole())) {
            throw new ForbiddenException(
                    "只能启用或停用普通运营账号"
            );
        }

        Integer targetStatus = dto.getStatus();

        // 6. 已经是目标状态，直接返回，不重复更新审计字段。
        if (targetStatus.equals(target.getStatus())) {
            return toEmployeeVO(target);
        }

        // 7. 只更新状态和修改审计字段。
        LocalDateTime now =
                LocalDateTime.now(ZoneId.of("Asia/Shanghai"));

        Long currentId = BaseContext.getCurrentId();

        int rows = employeeMapper.update(
                null,
                new LambdaUpdateWrapper<Employee>()
                        .eq(Employee::getId, id)
                        .eq(
                                Employee::getAccountRole,
                                EmployeeRoleConstants.OPERATOR
                        )
                        .set(Employee::getStatus, targetStatus)
                        .set(Employee::getUpdateTime, now)
                        .set(Employee::getUpdateUser, currentId)
        );

        if (rows != 1) {
            throw new IllegalStateException("更新运营账号状态失败");
        }

        // 8. 返回更新后的安全信息。
        target.setStatus(targetStatus);

        return toEmployeeVO(target);
    }

    private EmployeeVO toEmployeeVO(Employee employee) {
        return EmployeeVO.builder()
                .id(employee.getId())
                .username(employee.getUsername())
                .name(employee.getName())
                .phone(employee.getPhone())
                .status(employee.getStatus())
                .accountRole(employee.getAccountRole())
                .build();
    }

    @Override
    public PageResult<EmployeeVO> pageQuery(EmployeePageQueryDTO dto) {
        // 1. 只有启用的账号管理员可以管理普通运营账号。
        employeePermissionService.requireAccountManagement();

        // 2. 构建查询条件，只选择列表需要的安全字段。
        LambdaQueryWrapper<Employee> query =
                new LambdaQueryWrapper<Employee>()
                        .select(
                                Employee::getId,
                                Employee::getUsername,
                                Employee::getName,
                                Employee::getPhone,
                                Employee::getStatus,
                                Employee::getAccountRole
                        )
                        .eq(
                                Employee::getAccountRole,
                                EmployeeRoleConstants.OPERATOR
                        );

        // 3. 有状态条件时才筛选。
        if (dto.getStatus() != null) {
            query.eq(Employee::getStatus, dto.getStatus());
        }

        // 4. 关键词匹配姓名或者账号。
        String keyword = dto.getName();

        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + escapeLikeKeyword(keyword) + "%";

            query.and(condition -> condition.apply(
                    "name LIKE {0} ESCAPE '!' "
                            + "OR username LIKE {0} ESCAPE '!'",
                    pattern
            ));
        }

        // 5. 和现有列表保持一致，按id倒序。
        query.orderByDesc(Employee::getId);

        // 6. 创建分页参数，执行数据库分页。
        Page<Employee> page = new Page<>(
                dto.getPage(),
                dto.getPageSize()
        );

        employeeMapper.selectPage(page, query);

        // 7. Entity转换为安全VO。
        List<EmployeeVO> records = page.getRecords().stream()
                .map(this::toEmployeeVO)
                .toList();

        // 8. total是全部匹配数量，records是当前页数据。
        return new PageResult<>(page.getTotal(), records);
    }

    /**
     * 使用!作为LIKE转义字符。
     * 用户输入的%、_按普通字符搜索。
     */
    private String escapeLikeKeyword(String keyword) {
        return keyword
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    @Override
    public Employee login(EmployeeLoginDTO loginDTO) {

        Employee employee = employeeMapper.selectOne(
                new LambdaQueryWrapper<Employee>()
                        .eq(
                                Employee::getUsername,
                                loginDTO.getUsername().trim()
                        )
        );

        if (employee == null) {
            throw new BusinessException("用户名或密码错误");
        }

        boolean passwordCorrect = passwordEncoder.matches(
                loginDTO.getPassword(),
                employee.getPassword()
        );

        if (!passwordCorrect) {
            throw new BusinessException("用户名或密码错误");
        }

        if (!Integer.valueOf(1).equals(employee.getStatus())) {
            throw new BusinessException("账号已停用");
        }

        return employee;
    }

    @Override
    public EmployeeVO create(EmployeeCreateDTO dto) {
        // 只有账号管理员可以新增普通运营账号。
        employeePermissionService.requireAccountManagement();

        String rawPassword = dto.getInitialPassword();
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("初始密码的UTF-8长度不能超过72字节");
        }

        String username = dto.getUsername();
        if (usernameExists(username)) {
            throw new ConflictException("用户名已存在");
        }

        String encodedPassword = passwordEncoder.encode(rawPassword);
        Long currentOperatorId = BaseContext.getCurrentId();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));

        Employee employee = new Employee();
        employee.setUsername(username);
        employee.setName(dto.getName());
        employee.setPhone(dto.getPhone());
        employee.setStatus(dto.getStatus());
        employee.setPassword(encodedPassword);
        employee.setAccountRole(EmployeeRoleConstants.OPERATOR);
        employee.setCreateTime(now);
        employee.setUpdateTime(now);
        employee.setCreateUser(currentOperatorId);
        employee.setUpdateUser(currentOperatorId);

        // 数据库唯一索引兜底处理并发创建相同用户名的情况。
        try {
            int rows = employeeMapper.insert(employee);
            if (rows != 1) {
                throw new IllegalStateException("新增运营账号写入失败");
            }
        } catch (DuplicateKeyException exception) {
            if (usernameExists(username)) {
                throw new ConflictException("用户名已存在");
            }
            throw exception;
        }

        return toEmployeeVO(employee);
    }

    private boolean usernameExists(String username) {
        Long count = employeeMapper.selectCount(
                new LambdaQueryWrapper<Employee>()
                        .eq(Employee::getUsername, username)
        );
        return count != null && count > 0;
    }

    @Override
    public List<EmployeeVO> listOperators() {
        employeePermissionService.requireAccountManagement();

        List<Employee> employees = employeeMapper.selectList(
                new LambdaQueryWrapper<Employee>()
                        .select(
                                Employee::getId,
                                Employee::getUsername,
                                Employee::getName,
                                Employee::getPhone,
                                Employee::getStatus,
                                Employee::getAccountRole
                        )
                        .eq(Employee::getAccountRole, EmployeeRoleConstants.OPERATOR)
                        .orderByDesc(Employee::getId)
        );

        return employees.stream()
                .map(this::toEmployeeVO)
                .toList();
    }
}
