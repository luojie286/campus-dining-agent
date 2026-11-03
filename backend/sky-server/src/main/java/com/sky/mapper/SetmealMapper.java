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
    @Override
    @AutoFill(OperationType.INSERT)
    int insert(Setmeal setmeal);
    @Override
    @AutoFill(OperationType.UPDATE)
    int update(@Param(Constants.ENTITY) Setmeal audit,
               @Param(Constants.WRAPPER) Wrapper<Setmeal> wrapper);
}
