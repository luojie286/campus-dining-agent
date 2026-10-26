package com.sky.context;

public final class BaseContext {
    private static final ThreadLocal<Long> CURRENT_ID =
            new ThreadLocal<>();

    private BaseContext() {}

    public static void setCurrentId(Long id) {
        CURRENT_ID.set(id);
    }

    public static Long getCurrentId() {
        return CURRENT_ID.get();
    }

    public static void removeCurrentId() {
        CURRENT_ID.remove();
    }
}
//不能被继承，因为是工具类，不需要继承功能
