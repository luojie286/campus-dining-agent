package com.sky;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.sky.aspect.AutoFillAspect;
import com.sky.context.BaseContext;
import com.sky.entity.Category;
import com.sky.entity.Employee;
import com.sky.mapper.CategoryMapper;
import com.sky.mapper.EmployeeMapper;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 使用真正的Spring AOP、MyBatis Mapper代理和SQL生成器。
 * 在Executor写入前截获SQL，不连接或修改用户数据库。
 */
class AutoFillMapperTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 10, 28, 8, 0);

    private AnnotationConfigApplicationContext context;
    private EmployeeMapper employees;
    private CategoryMapper categories;
    private SqlCapture capture;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(MapperConfiguration.class);
        employees = context.getBean(EmployeeMapper.class);
        categories = context.getBean(CategoryMapper.class);
        capture = context.getBean(SqlCapture.class);
        BaseContext.setCurrentId(7L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
        if (context != null) {
            context.close();
        }
    }

    @Test
    void employeeInsertPassesThroughSpringProxyAndWritesAllAuditFields() {
        assertThat(AopUtils.isAopProxy(employees)).isTrue();
        Employee employee = new Employee();
        employee.setUsername("audit_test");
        employee.setCreateUser(999L);
        employees.insert(employee);

        assertThat(employee.getCreateTime()).isEqualTo(NOW);
        assertThat(employee.getUpdateTime()).isEqualTo(NOW);
        assertThat(employee.getCreateUser()).isEqualTo(7L);
        assertThat(employee.getUpdateUser()).isEqualTo(7L);
        assertThat(capture.sql).contains("INSERT INTO employee", "create_time",
                "update_time", "create_user", "update_user");
        assertThat(capture.values).contains(NOW, 7L, "audit_test");
    }

    @Test
    void employeeWrapperUpdateOnlyWritesSelectedBusinessAndAuditFields() {
        Employee audit = new Employee();
        employees.update(audit, new LambdaUpdateWrapper<Employee>()
                .eq(Employee::getId, 50L)
                .eq(Employee::getAccountRole, "OPERATOR")
                .set(Employee::getStatus, 0));

        assertThat(audit.getCreateTime()).isNull();
        assertThat(audit.getCreateUser()).isNull();
        assertThat(audit.getUpdateTime()).isEqualTo(NOW);
        assertThat(audit.getUpdateUser()).isEqualTo(7L);
        assertThat(capture.sql).contains("UPDATE employee", "update_time",
                "update_user", "status", "WHERE", "account_role");
        assertThat(capture.sql).doesNotContain("create_time", "create_user",
                "password", "username", "phone");
        assertThat(capture.values).contains(NOW, 7L, 50L, "OPERATOR", 0);
    }

    @Test
    void categoryInsertAndUpdateUseCorrectStatementsAndAuditFields() {
        Category category = new Category();
        category.setName("测试分类");
        categories.insert(category);
        assertThat(capture.sql).startsWith("INSERT INTO category");
        assertThat(category.getCreateUser()).isEqualTo(7L);
        assertThat(category.getCreateTime()).isEqualTo(NOW);

        Category audit = new Category();
        categories.update(audit, new LambdaUpdateWrapper<Category>()
                .eq(Category::getId, 20L)
                .set(Category::getName, "新名称"));
        assertThat(capture.sql).contains("UPDATE category", "update_time", "update_user");
        assertThat(capture.sql).doesNotContain("create_time", "create_user", "sort", "status");
        assertThat(capture.values).contains(NOW, 7L, 20L, "新名称");
        assertThat(audit.getCreateTime()).isNull();
    }

    @Test
    void missingIdentityStopsBeforeSqlExecution() {
        BaseContext.removeCurrentId();
        assertThatThrownBy(() -> employees.insert(new Employee()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少可信");
        assertThat(capture.sql).isNull();
    }

    @Test
    void nullUpdateEntityStopsBeforeSqlExecution() {
        assertThatThrownBy(() -> categories.update(null,
                new LambdaUpdateWrapper<Category>().eq(Category::getId, 20L)
                        .set(Category::getStatus, 0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能为空");
        assertThat(capture.sql).isNull();
    }

    @TestConfiguration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import(AutoFillAspect.class)
    static class MapperConfiguration {

        @Bean
        Clock businessClock() {
            return Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"),
                    ZoneId.of("Asia/Shanghai"));
        }

        @Bean
        SqlCapture sqlCapture() {
            return new SqlCapture();
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(SqlCapture capture) throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(mock(DataSource.class));
            factory.setConfiguration(new MybatisConfiguration());
            factory.setPlugins(capture);
            SqlSessionFactory sessions = factory.getObject();
            sessions.getConfiguration().addMapper(EmployeeMapper.class);
            sessions.getConfiguration().addMapper(CategoryMapper.class);
            return sessions;
        }

        @Bean
        EmployeeMapper employeeMapper(SqlSessionFactory sessions) {
            return new SqlSessionTemplate(sessions).getMapper(EmployeeMapper.class);
        }

        @Bean
        CategoryMapper categoryMapper(SqlSessionFactory sessions) {
            return new SqlSessionTemplate(sessions).getMapper(CategoryMapper.class);
        }
    }

    @Intercepts(@Signature(type = Executor.class, method = "update",
            args = {MappedStatement.class, Object.class}))
    static class SqlCapture implements Interceptor {
        private String sql;
        private final List<Object> values = new ArrayList<>();

        @Override
        public Object intercept(Invocation invocation) {
            MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
            Object parameter = invocation.getArgs()[1];
            BoundSql boundSql = statement.getBoundSql(parameter);
            sql = boundSql.getSql().replaceAll("\\s+", " ").trim();
            values.clear();
            for (ParameterMapping mapping : boundSql.getParameterMappings()) {
                String property = mapping.getProperty();
                Object value;
                if (boundSql.hasAdditionalParameter(property)) {
                    value = boundSql.getAdditionalParameter(property);
                } else {
                    value = statement.getConfiguration().newMetaObject(parameter)
                            .getValue(property);
                }
                values.add(value);
            }
            // 返回模拟影响行数，阻止任何实际JDBC写入。
            return 1;
        }
    }
}
