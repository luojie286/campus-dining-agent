package com.sky.mapper;

import com.sky.dto.AddressSaveDTO;
import com.sky.vo.AddressVO;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
@Profile("!bootstrap")
public class AddressBookRepository {
    private static final String SELECT = """
            SELECT id,user_id,consignee,phone,building,detail,is_default
            FROM address_book WHERE user_id=?
            """;
    private static final RowMapper<AddressVO> ADDRESS = (row, index) -> new AddressVO(
            row.getLong("id"), row.getLong("user_id"), row.getString("consignee"),
            row.getString("phone"), row.getString("building"), row.getString("detail"),
            row.getBoolean("is_default"));

    private final JdbcTemplate jdbc;

    public AddressBookRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<AddressVO> list(long userId) {
        return jdbc.query(SELECT + " ORDER BY is_default DESC,id DESC", ADDRESS, userId);
    }

    public Optional<AddressVO> find(long userId, long id) {
        return jdbc.query(SELECT + " AND id=?", ADDRESS, userId, id).stream().findFirst();
    }

    public long count(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM address_book WHERE user_id=?", Long.class, userId);
    }

    public long insert(long userId, AddressSaveDTO dto) {
        var keys = new GeneratedKeyHolder();
        int inserted = jdbc.update(connection -> {
            var sql = connection.prepareStatement("""
                    INSERT INTO address_book(user_id,consignee,phone,building,detail,is_default)
                    VALUES (?,?,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            sql.setLong(1, userId);
            sql.setString(2, dto.getConsignee());
            sql.setString(3, dto.getPhone());
            sql.setString(4, dto.getBuilding());
            sql.setString(5, dto.getDetail());
            sql.setBoolean(6, dto.getIsDefault());
            return sql;
        }, keys);
        Number key = keys.getKey();
        if (inserted != 1 || key == null) throw new IllegalStateException("保存地址失败");
        return key.longValue();
    }

    public void update(long userId, AddressSaveDTO dto) {
        jdbc.update("""
                UPDATE address_book SET consignee=?,phone=?,building=?,detail=?,is_default=?
                WHERE user_id=? AND id=?
                """, dto.getConsignee(), dto.getPhone(), dto.getBuilding(), dto.getDetail(),
                dto.getIsDefault(), userId, dto.getId());
    }

    public void clearDefault(long userId) {
        jdbc.update("UPDATE address_book SET is_default=0 WHERE user_id=? AND is_default=1", userId);
    }

    public void setDefault(long userId, long id) {
        jdbc.update("UPDATE address_book SET is_default=1 WHERE user_id=? AND id=?", userId, id);
    }

    public int delete(long userId, long id) {
        return jdbc.update("DELETE FROM address_book WHERE user_id=? AND id=?", userId, id);
    }
}
