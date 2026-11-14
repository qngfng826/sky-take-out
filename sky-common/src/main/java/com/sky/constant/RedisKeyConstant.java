package com.sky.constant;

/**
 * Redis 缓存 key 常量 —— 亮点改造①：缓存三件套
 *
 * 统一管理缓存 key 前缀：用户端写缓存、管理端清缓存引用同一个常量，
 * 避免课程原版中 key 字符串散落在多个类里（"dish_" 写死了 5 处），
 * 一旦改动前缀漏改一处就会出现"写了清不掉"的脏缓存。
 */
public class RedisKeyConstant {

    /** C 端菜品缓存 key 前缀，完整 key 形如 cache:dish:16 */
    public static final String CACHE_DISH_KEY = "cache:dish:";

    /** C 端套餐缓存 key 前缀，完整 key 形如 cache:setmeal:16 */
    public static final String CACHE_SETMEAL_KEY = "cache:setmeal:";

    private RedisKeyConstant() {
    }
}
