package com.sky;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.sky.dto.CategorySaveDTO;
import com.sky.dto.CategoryStatusUpdateDTO;
import com.sky.dto.EmployeeStatusUpdateDTO;
import com.sky.dto.EmployeeUpdateDTO;
import com.sky.entity.Category;
import com.sky.entity.Employee;
import com.sky.mapper.CategoryMapper;
import com.sky.mapper.EmployeeMapper;
import com.sky.service.EmployeePermissionService;
import com.sky.service.impl.CategoryServiceImpl;
import com.sky.service.impl.EmployeeServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 验证迁移填充机制后，同值操作不写库，实际更新不回写完整目标实体。 */
class AuditServiceTest {

    @BeforeAll
    static void initializeLambdaMetadata() {
        // Mapper被mock后不会自动注册实体元数据，显式初始化以独立运行测试。
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "audit-service-test");
        assistant.setCurrentNamespace("audit-service-test");
        TableInfoHelper.initTableInfo(assistant, Employee.class);
        TableInfoHelper.initTableInfo(assistant, Category.class);
    }

    @Test
    void employeeNoOpDoesNotWriteAndChangedProfileUsesFreshAuditEntity() {
        EmployeeMapper mapper = mock(EmployeeMapper.class);
        Employee target = new Employee();
        target.setId(50L);
        target.setAccountRole("OPERATOR");
        target.setName("原姓名");
        target.setPhone("13800000000");
        target.setStatus(1);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(target);
        when(mapper.update(any(Employee.class), any(Wrapper.class))).thenReturn(1);
        EmployeeServiceImpl service = new EmployeeServiceImpl(mapper,
                mock(PasswordEncoder.class), mock(EmployeePermissionService.class));

        EmployeeUpdateDTO dto = new EmployeeUpdateDTO();
        dto.setId(50L);
        dto.setName("原姓名");
        dto.setPhone("13800000000");
        service.update(dto);
        EmployeeStatusUpdateDTO status = new EmployeeStatusUpdateDTO();
        status.setStatus(1);
        service.updateStatus(50L, status);
        verify(mapper, never()).update(any(Employee.class), any(Wrapper.class));

        dto.setName("新姓名");
        service.update(dto);
        ArgumentCaptor<Employee> entity = ArgumentCaptor.forClass(Employee.class);
        verify(mapper).update(entity.capture(), any(Wrapper.class));
        assertThat(entity.getValue()).isNotSameAs(target);
        assertThat(entity.getValue().getName()).isNull();
        assertThat(entity.getValue().getAccountRole()).isNull();
        assertThat(entity.getValue().getPassword()).isNull();
    }

    @Test
    void categoryNoOpDoesNotWriteAndChangedStatusUsesFreshAuditEntity() {
        CategoryMapper mapper = mock(CategoryMapper.class);
        Category target = new Category();
        target.setId(20L);
        target.setName("分类");
        target.setType(1);
        target.setSort(0);
        target.setStatus(1);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(target);
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(mapper.update(any(Category.class), any(Wrapper.class))).thenReturn(1);
        CategoryServiceImpl service = new CategoryServiceImpl(mapper,
                mock(EmployeePermissionService.class), mock(com.sky.cache.MenuPageCache.class));

        CategorySaveDTO dto = new CategorySaveDTO();
        dto.setId(20L);
        dto.setName("分类");
        dto.setType(1);
        dto.setSort(0);
        dto.setStatus(1);
        service.update(dto);
        CategoryStatusUpdateDTO status = new CategoryStatusUpdateDTO();
        status.setStatus(1);
        service.updateStatus(20L, status);
        verify(mapper, never()).update(any(Category.class), any(Wrapper.class));

        status.setStatus(0);
        service.updateStatus(20L, status);
        ArgumentCaptor<Category> entity = ArgumentCaptor.forClass(Category.class);
        verify(mapper).update(entity.capture(), any(Wrapper.class));
        assertThat(entity.getValue()).isNotSameAs(target);
        assertThat(entity.getValue().getName()).isNull();
        assertThat(entity.getValue().getCreateTime()).isNull();
    }
}
