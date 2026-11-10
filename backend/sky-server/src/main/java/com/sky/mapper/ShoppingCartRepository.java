package com.sky.mapper;

import com.sky.vo.CartLineVO;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
@Profile("!bootstrap")
public class ShoppingCartRepository {
    private final JdbcTemplate jdbc;

    public ShoppingCartRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CartLineVO> list(long userId) {
        return jdbc.query("""
                SELECT CASE WHEN dish_id IS NOT NULL THEN dish_id * 2
                            ELSE setmeal_id * 2 + 1 END AS item_id, number
                FROM shopping_cart WHERE user_id=? ORDER BY item_id
                """, (row, index) -> new CartLineVO(
                row.getLong("item_id"), row.getInt("number")), userId);
    }

    public void clear(long userId) {
        jdbc.update("DELETE FROM shopping_cart WHERE user_id=?", userId);
    }

    public void remove(long userId, long itemId) {
        if (isDish(itemId)) {
            jdbc.update("DELETE FROM shopping_cart WHERE user_id=? AND dish_id=?", userId, rawId(itemId));
        } else {
            jdbc.update("DELETE FROM shopping_cart WHERE user_id=? AND setmeal_id=?", userId, rawId(itemId));
        }
    }

    public void updateQuantity(long userId, long itemId, int quantity) {
        if (isDish(itemId)) {
            jdbc.update("UPDATE shopping_cart SET number=? WHERE user_id=? AND dish_id=?", quantity, userId, rawId(itemId));
        } else {
            jdbc.update("UPDATE shopping_cart SET number=? WHERE user_id=? AND setmeal_id=?", quantity, userId, rawId(itemId));
        }
    }

    public void insert(long userId, long itemId, int quantity, ProductSnapshot product) {
        long id = rawId(itemId);
        Long dishId = isDish(itemId) ? id : null;
        Long setmealId = isDish(itemId) ? null : id;
        jdbc.update("""
                INSERT INTO shopping_cart(user_id,dish_id,setmeal_id,number,name,image,amount)
                VALUES (?,?,?,?,?,?,?)
                """, userId, dishId, setmealId, quantity, product.name(), product.image(), product.price());
    }

    public static boolean isDish(long itemId) { return itemId % 2 == 0; }

    public static long rawId(long itemId) { return itemId / 2; }

    public record ProductSnapshot(String name, String image, BigDecimal price) {}
}
