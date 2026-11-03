package com.sky.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.sky.annotation.AutoFill;
import com.sky.entity.Setmeal;
import com.sky.enumeration.OperationType;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SetmealMapper extends BaseMapper<Setmeal> {
    com.baomidou.mybatisplus.core.metadata.IPage<Setmeal> selectSetmealPage(
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Setmeal> page,
            @Param("query") com.sky.dto.SetmealPageQueryDTO query,
            @Param("namePattern") String namePattern);

    @Select("SELECT od.id FROM order_detail od JOIN orders o ON o.id=od.order_id "
            + "WHERE od.setmeal_id=#{id} AND o.status IN (1,2,3,4,7) LIMIT 1 FOR SHARE")
    Long pendingReference(@Param("id") Long id);

    @Override
    @AutoFill(OperationType.INSERT)
    int insert(Setmeal setmeal);
    @Override
    @AutoFill(OperationType.UPDATE)
    int update(@Param(Constants.ENTITY) Setmeal audit,
               @Param(Constants.WRAPPER) Wrapper<Setmeal> wrapper);
}
