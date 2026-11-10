package com.sky.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.exception.ConflictException;
import com.sky.vo.CartLineVO;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Profile("!bootstrap")
public class CartReplayRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public CartReplayRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    // 读写均在持有同一用户行锁的事务内，购物车变更与幂等结果一起提交。
    public Optional<List<CartLineVO>> find(long userId, String key, long itemId, int quantity) {
        var saved = jdbc.query("""
                SELECT item_id,quantity,response_json FROM cart_idempotency
                WHERE user_id=? AND request_key=? AND expires_at > CURRENT_TIMESTAMP
                """, (row, index) -> new SavedRequest(row.getLong("item_id"),
                row.getInt("quantity"), row.getString("response_json")), userId, key);
        if (saved.isEmpty()) return Optional.empty();
        SavedRequest previous = saved.getFirst();
        if (previous.itemId() != itemId || previous.quantity() != quantity) {
            throw new ConflictException("同一幂等键不能用于不同的加购请求");
        }
        try {
            return Optional.of(json.readValue(previous.response(), new TypeReference<List<CartLineVO>>() {}));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("购物车幂等记录格式错误", exception);
        }
    }

    public void save(long userId, String key, long itemId, int quantity, List<CartLineVO> result) {
        try {
            String response = json.writeValueAsString(result);
            jdbc.update("DELETE FROM cart_idempotency WHERE user_id=? AND request_key=? AND expires_at<=CURRENT_TIMESTAMP",
                    userId, key);
            jdbc.update("""
                    INSERT INTO cart_idempotency(user_id,request_key,item_id,quantity,response_json,expires_at)
                    VALUES (?,?,?,?,?,CURRENT_TIMESTAMP + INTERVAL 24 HOUR)
                    """, userId, key, itemId, quantity, response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("购物车幂等响应无法保存", exception);
        }
    }

    private record SavedRequest(long itemId, int quantity, String response) {}
}
