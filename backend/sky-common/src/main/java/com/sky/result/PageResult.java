package com.sky.result;

import java.util.List;

/** 分页协议；SQL、分页校验及权限范围由学习者实现。 */
public record PageResult<T>(long total, List<T> records) {}
