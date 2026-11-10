package com.sky.service;

import com.sky.context.BaseContext;
import com.sky.exception.ConflictException;
import com.sky.exception.ForbiddenException;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.SQLException;
import java.util.List;

/** 购物车、地址簿与下单共用此锁，避免同一用户的业务操作交错。 */
@Component
@Profile("!bootstrap")
public class UserTransactionLock {
    private final JdbcTemplate jdbc;

    public UserTransactionLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long currentUserId() {
        Long id = BaseContext.getCurrentId();
        if (id == null || id <= 0) throw new ForbiddenException("请先登录");
        return id;
    }

    public long lockCurrentUser(boolean failIfBusy) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("用户行锁必须在事务内获取");
        }
        long id = currentUserId();
        String sql = "SELECT status FROM `user` WHERE id=? FOR UPDATE";
        if (failIfBusy) sql += " NOWAIT";

        List<Integer> states;
        try {
            states = jdbc.queryForList(sql, Integer.class, id);
        } catch (DataAccessException exception) {
            Throwable cause = exception.getMostSpecificCause();
            if (exception instanceof PessimisticLockingFailureException
                    || (cause instanceof SQLException sqlError && sqlError.getErrorCode() == 3572)) {
                throw new ConflictException("账号操作正在处理，请稍后重试");
            }
            throw exception;
        }
        if (states.isEmpty() || states.getFirst() != 1) {
            throw new ForbiddenException("账号不存在或已停用");
        }
        return id;
    }
}
