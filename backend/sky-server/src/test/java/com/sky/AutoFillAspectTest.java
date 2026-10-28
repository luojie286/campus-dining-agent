package com.sky;

import com.sky.annotation.AutoFill;
import com.sky.aspect.AutoFillAspect;
import com.sky.context.BaseContext;
import com.sky.entity.Employee;
import com.sky.enumeration.OperationType;
import org.aspectj.lang.JoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AutoFillAspectTest {
    private final AutoFillAspect aspect = new AutoFillAspect(Clock.fixed(
            Instant.parse("2026-10-28T00:00:00Z"), ZoneId.of("Asia/Shanghai")));

    @AfterEach
    void clearIdentity() {
        BaseContext.removeCurrentId();
    }

    @Test
    void updateOverwritesOldModificationValuesAndPreservesCreationValues() {
        BaseContext.setCurrentId(7L);
        Employee employee = new Employee();
        LocalDateTime original = LocalDateTime.of(2020, 1, 1, 0, 0);
        employee.setCreateTime(original);
        employee.setCreateUser(2L);
        employee.setUpdateTime(original);
        employee.setUpdateUser(3L);

        aspect.fillFields(joinPoint("other parameter", employee), annotation(1));

        assertThat(employee.getCreateTime()).isEqualTo(original);
        assertThat(employee.getCreateUser()).isEqualTo(2L);
        assertThat(employee.getUpdateTime()).isEqualTo(LocalDateTime.of(2026, 10, 28, 8, 0));
        assertThat(employee.getUpdateUser()).isEqualTo(7L);
    }

    @Test
    void invalidEntityIndexFailsClearly() {
        assertThatThrownBy(() -> aspect.fillFields(joinPoint(new Employee()), annotation(1)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("位置不合法");
        assertThatThrownBy(() -> aspect.fillFields(joinPoint(new Employee()), annotation(-1)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("位置不合法");
    }

    @Test
    void missingSetterFailsRatherThanSilentlyContinuing() {
        BaseContext.setCurrentId(7L);
        assertThatThrownBy(() -> aspect.fillFields(joinPoint(new Object()), annotation(0)))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(NoSuchMethodException.class);
    }

    @Test
    void nonPositiveIdentityIsRejected() {
        BaseContext.setCurrentId(0L);
        assertThatThrownBy(() -> aspect.fillFields(joinPoint(new Employee()), annotation(0)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("缺少可信");
    }

    private AutoFill annotation(int index) {
        AutoFill annotation = mock(AutoFill.class);
        when(annotation.entityIndex()).thenReturn(index);
        when(annotation.value()).thenReturn(OperationType.UPDATE);
        return annotation;
    }

    private JoinPoint joinPoint(Object... arguments) {
        JoinPoint joinPoint = mock(JoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(arguments);
        return joinPoint;
    }
}
