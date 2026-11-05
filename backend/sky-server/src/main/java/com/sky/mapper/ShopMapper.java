package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sky.entity.Shop;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ShopMapper extends BaseMapper<Shop> {

    @Select("""
            SELECT id, name, status, opening_hours, pickup_point
            FROM shop
            WHERE id = #{id}
            FOR UPDATE
            """)
    Shop selectForUpdate(@Param("id") long id);

    @Update("""
            UPDATE shop
            SET status = #{status}
            WHERE id = #{id}
            """)
    int updateStatus(@Param("id") long id, @Param("status") int status);
}