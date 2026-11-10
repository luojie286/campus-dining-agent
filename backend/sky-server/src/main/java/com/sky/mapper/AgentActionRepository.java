package com.sky.mapper;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Agent 写操作确认记录：状态流转用条件更新，重复确认不会重复执行业务。 */
@Repository
@Profile("!bootstrap")
public class AgentActionRepository {

    public static final int PENDING = 0;
    public static final int SUCCESS = 1;
    public static final int CANCELLED = 2;
    public static final int EXPIRED = 3;
    public static final int FAILED = 4;
    public static final int EXECUTING = 5;

    public record Row(long id, String actionId, long actorId, String actorRole,
                      String sessionId, String toolName, String params, String label,
                      String detail, int status, String result, LocalDateTime expireTime,
                      LocalDateTime confirmTime) {
    }

    private static final String COLUMNS = """
            id,action_id,actor_id,actor_role,session_id,tool_name,params,label,detail,
            status,result,expire_time,confirm_time
            """;

    private static final RowMapper<Row> MAPPER = (row, index) -> new Row(
            row.getLong("id"), row.getString("action_id"), row.getLong("actor_id"),
            row.getString("actor_role"), row.getString("session_id"),
            row.getString("tool_name"), row.getString("params"),
            row.getString("label"), row.getString("detail"), row.getInt("status"),
            row.getString("result"), row.getObject("expire_time", LocalDateTime.class),
            row.getObject("confirm_time", LocalDateTime.class));

    private final JdbcTemplate jdbc;

    public AgentActionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String actionId, long actorId, String actorRole, String sessionId,
            String toolName, String params, String label, String detail, LocalDateTime expireTime) {
        jdbc.update("""
                INSERT INTO agent_action(action_id,actor_id,actor_role,session_id,tool_name,
                  params,label,detail,status,expire_time)
                VALUES (?,?,?,?,?,?,?,?,0,?)
                """, actionId, actorId, actorRole, sessionId, toolName, params,
                label, detail, expireTime);
    }

    public Optional<Row> find(String actionId) {
        return find(actionId, false);
    }

    public Optional<Row> findForUpdate(String actionId) {
        return find(actionId, true);
    }

    private Optional<Row> find(String actionId, boolean lock) {
        String suffix = lock ? " FOR UPDATE" : "";
        List<Row> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM agent_action WHERE action_id=?" + suffix,
                MAPPER, actionId);
        return rows.stream().findFirst();
    }

    /** 只有本人、待确认且未过期的记录才能进入执行中，避免并发重复执行。 */
    public boolean markExecuting(String actionId, long actorId, String actorRole, LocalDateTime now) {
        int rows = jdbc.update("""
                UPDATE agent_action SET status=5
                WHERE action_id=? AND status=0 AND expire_time>?
                  AND actor_id=? AND actor_role=?
                """, actionId, now, actorId, actorRole);
        return rows == 1;
    }

    public void markSuccess(String actionId, String result, LocalDateTime now) {
        int rows = jdbc.update("UPDATE agent_action SET status=1,result=?,confirm_time=? WHERE action_id=? AND status=5",
                result, now, actionId);
        if (rows != 1) throw new IllegalStateException("保存确认结果失败");
    }

    public boolean cancel(String actionId, long actorId, String actorRole, String sessionId) {
        int rows = jdbc.update("""
                UPDATE agent_action SET status=2
                WHERE action_id=? AND status=0 AND actor_id=? AND actor_role=? AND session_id=?
                """, actionId, actorId, actorRole, sessionId);
        return rows == 1;
    }

    /** 删除会话时让它下面的待确认卡片一起失效，不回滚已经执行成功的业务。 */
    public int cancelBySession(String sessionId, long actorId, String actorRole) {
        return jdbc.update("""
                UPDATE agent_action SET status=2
                WHERE session_id=? AND actor_id=? AND actor_role=? AND status=0
                """, sessionId, actorId, actorRole);
    }
}
