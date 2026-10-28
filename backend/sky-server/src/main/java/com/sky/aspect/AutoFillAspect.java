package com.sky.aspect;

import com.sky.annotation.AutoFill;
import com.sky.context.BaseContext;
import com.sky.enumeration.OperationType;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.LocalDateTime;

@Aspect
@Component
public class AutoFillAspect {

    private final Clock businessClock;

    public AutoFillAspect(
            @Qualifier("businessClock") Clock businessClock) {
        this.businessClock = businessClock;
    }

    /**
     * 匹配当前项目 Mapper 包及其子包中的方法。
     */
    @Pointcut("execution(* com.sky.mapper..*(..))")
    public void mapperMethod() {
    }

    /**
     * 仅处理带有 AutoFill 注解的 Mapper 方法。
     */
    @Before(
            value = "mapperMethod() && @annotation(autoFill)",
            argNames = "joinPoint,autoFill"
    )
    public void fillFields(
            JoinPoint joinPoint,
            AutoFill autoFill) {

        // 注解声明实体位置；空实体或错误位置必须在执行SQL前失败。
        Object entity = getEntity(joinPoint, autoFill);

        Long currentOperatorId = BaseContext.getCurrentId();

        if (currentOperatorId == null || currentOperatorId <= 0) {
            throw new IllegalStateException(
                    "公共字段填充失败：缺少可信的运营操作人"
            );
        }

        // 同一次写入只取一次时间，保证新增时创建和修改时间一致。
        LocalDateTime now = LocalDateTime.now(businessClock);

        try {
            Class<?> entityClass = entity.getClass();

            Method setUpdateTime = entityClass.getMethod(
                    "setUpdateTime",
                    LocalDateTime.class
            );

            Method setUpdateUser = entityClass.getMethod(
                    "setUpdateUser",
                    Long.class
            );

            if (autoFill.value() == OperationType.INSERT) {
                Method setCreateTime = entityClass.getMethod(
                        "setCreateTime",
                        LocalDateTime.class
                );

                Method setCreateUser = entityClass.getMethod(
                        "setCreateUser",
                        Long.class
                );

                setCreateTime.invoke(entity, now);
                setCreateUser.invoke(entity, currentOperatorId);
            }

            setUpdateTime.invoke(entity, now);
            setUpdateUser.invoke(entity, currentOperatorId);

        } catch (ReflectiveOperationException exception) {
            // 不吞掉反射异常：前置通知失败会阻止Mapper写入。
            throw new IllegalStateException(
                    "公共字段填充失败，请检查实体审计字段及setter",
                    exception
            );
        }
    }

    private Object getEntity(
            JoinPoint joinPoint,
            AutoFill autoFill) {

        Object[] arguments = joinPoint.getArgs();
        int entityIndex = autoFill.entityIndex();

        if (entityIndex < 0 || entityIndex >= arguments.length) {
            throw new IllegalStateException(
                    "公共字段填充失败：实体参数位置不合法"
            );
        }

        Object entity = arguments[entityIndex];

        if (entity == null) {
            throw new IllegalStateException(
                    "公共字段填充失败：实体参数不能为空"
            );
        }

        return entity;
    }
}
