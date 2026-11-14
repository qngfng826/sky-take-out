package com.sky.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 逻辑过期缓存的包装对象 —— 亮点改造①：缓存三件套
 *
 * 逻辑过期方案的缓存结构：key 在 Redis 中不设置 TTL（永不过期），
 * 改为在 value 里记录一个"逻辑过期时间"字段。
 * 过期后不删除、不阻塞，返回旧数据并由异步线程重建——用短暂的数据陈旧换取高可用。
 */
@Data
public class RedisData {

    /** 逻辑过期时间 */
    private LocalDateTime expireTime;

    /** 真实业务数据（菜品、套餐等） */
    private Object data;
}
