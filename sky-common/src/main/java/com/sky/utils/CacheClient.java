package com.sky.utils;

import com.alibaba.fastjson.JSON;
import com.sky.entity.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 通用缓存工具类 —— 亮点改造①：缓存三件套
 *
 * <p>位于 sky-common 公共模块：与 JwtUtil/AliOssUtil 等工具类同级，
 * 后续其他模块（如微服务拆分后的商品服务）也能直接复用。
 * 依赖注入在运行时由使用方模块提供（sky-server 已有 Redis 自动配置）。
 *
 * <p>解决课程原版缓存的两大问题：
 * <ul>
 *   <li><b>缓存穿透</b>：恶意请求大量数据库中不存在的 id（如 categoryId=-999），每次都穿透缓存打到数据库。
 *       方案：查库为空时缓存一个空字符串占位、并给更短的过期时间，之后相同 id 的请求在 Redis 层直接被拦截。</li>
 *   <li><b>缓存击穿</b>：某个热点 key 过期的瞬间，大量并发同时未命中、同时查库重建，瞬时压垮数据库。
 *       方案：互斥锁（Redis setIfAbsent），同一时刻只放行一个线程查库重建，其余线程休眠重试后读别人重建好的缓存。</li>
 * </ul>
 *
 * <p>序列化用 fastjson + StringRedisTemplate（存 JSON 字符串）：
 * 相比课程原版 RedisTemplate 的 JDK 序列化，redis-cli 里可以直接读懂数据，且不要求实体实现 Serializable。
 *
 * <p>另实现 {@link #queryWithLogicalExpire} 逻辑过期方案作为对比（本项目当前未接线使用）：
 * 互斥锁方案保证强一致但需要等待；逻辑过期方案不阻塞、可用性高，代价是短暂返回旧数据且需要提前预热缓存。
 */
@Component
@Slf4j
public class CacheClient {

    /** 空值缓存的过期时间（秒）：占位数据无需长存，到期后可重新查库（万一之后数据真的存在了） */
    private static final long NULL_VALUE_TTL_SECONDS = 60;

    /** 互斥锁过期时间（秒）：防止持锁线程异常退出后锁永不释放（死锁） */
    private static final long LOCK_TTL_SECONDS = 10;

    /** 抢锁失败后的重试间隔（毫秒） */
    private static final long LOCK_RETRY_INTERVAL_MILLIS = 50;

    /** 逻辑过期方案中用于异步重建缓存的线程池 */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 写缓存：JSON 序列化 + 过期时间
     */
    public void set(String key, Object value, long ttl, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(value), ttl, unit);
    }

    /**
     * 查询缓存（单对象）：空值缓存防穿透 + 互斥锁防击穿
     *
     * @param keyPrefix  缓存 key 前缀，如 RedisKeyConstant.CACHE_DISH_KEY
     * @param id         业务 id（如分类 id）
     * @param type       返回值类型
     * @param dbFallback 缓存未命中时查库的函数
     * @param ttl        缓存过期时间
     * @param unit       时间单位
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                          Function<ID, R> dbFallback, long ttl, TimeUnit unit) {
        String key = keyPrefix + id;

        // 1. 查缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 命中有效值：直接返回，不查库
        if (json != null && !json.isEmpty()) {
            return JSON.parseObject(json, type);
        }
        // 3. 命中空值（""）：说明数据库确认过无此数据，直接返回 null，拦截穿透请求
        if (json != null) {
            return null;
        }

        // 4. 未命中：互斥锁 + 双重检查 + 查库重建
        String lockKey = "lock:" + key;
        while (true) {
            // 4.1 抢互斥锁（setIfAbsent 原子操作：只有一个线程能成功写入）
            if (tryLock(lockKey)) {
                try {
                    // 4.2 双重检查：等锁期间缓存可能已被其他线程重建好
                    json = stringRedisTemplate.opsForValue().get(key);
                    if (json != null && !json.isEmpty()) {
                        return JSON.parseObject(json, type);
                    }
                    if (json != null) {
                        return null;
                    }
                    // 4.3 查数据库
                    R r = dbFallback.apply(id);
                    // 4.4 写缓存：查不到则缓存空值（短 TTL）防穿透，查到则缓存正常数据
                    if (r == null) {
                        stringRedisTemplate.opsForValue().set(key, "", NULL_VALUE_TTL_SECONDS, TimeUnit.SECONDS);
                    } else {
                        set(key, r, ttl, unit);
                    }
                    return r;
                } finally {
                    // 4.5 无论成败都释放锁（finally 保证不把锁带进坟墓）
                    unlock(lockKey);
                }
            }
            // 没抢到锁：休眠后重试，重试后大概率命中持锁线程重建好的缓存
            try {
                Thread.sleep(LOCK_RETRY_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    /**
     * 查询缓存（List 版本）：菜品/套餐按分类查询用，防护逻辑与 {@link #queryWithPassThrough} 相同。
     * 注意：空 List（该分类下没有菜品）会被正常缓存为 "[]"，修复了课程原版"空结果不缓存、反复查库"的缺陷。
     */
    public <R, ID> List<R> queryWithPassThroughList(String keyPrefix, ID id, Class<R> itemType,
                                                    Function<ID, List<R>> dbFallback, long ttl, TimeUnit unit) {
        String key = keyPrefix + id;
        String lockKey = "lock:" + key;

        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null && !json.isEmpty()) {
            return JSON.parseArray(json, itemType);
        }
        if (json != null) {
            return new ArrayList<>(0);
        }

        while (true) {
            if (tryLock(lockKey)) {
                try {
                    json = stringRedisTemplate.opsForValue().get(key);
                    if (json != null && !json.isEmpty()) {
                        return JSON.parseArray(json, itemType);
                    }
                    if (json != null) {
                        return new ArrayList<>(0);
                    }
                    List<R> list = dbFallback.apply(id);
                    if (list == null) {
                        // 防御：list 类查询正常不会返回 null，这里兜底缓存空值
                        stringRedisTemplate.opsForValue().set(key, "", NULL_VALUE_TTL_SECONDS, TimeUnit.SECONDS);
                        return new ArrayList<>(0);
                    }
                    set(key, list, ttl, unit);
                    return list;
                } finally {
                    unlock(lockKey);
                }
            }
            try {
                Thread.sleep(LOCK_RETRY_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new ArrayList<>(0);
            }
        }
    }

    /**
     * 查询缓存（逻辑过期版）：防击穿的另一种思路，与互斥锁方案对比学习用。
     *
     * <p>流程：缓存永不过期（无 Redis TTL），value 里带逻辑过期时间。
     * 读到已过期数据时不阻塞、不删缓存，直接返回旧值，同时抢锁成功的一个线程在后台异步重建。
     *
     * <p>代价与前提：① 需要提前预热缓存（key 不存在时本方法不会查库，直接返回 null）；
     * ② 重建完成前的所有请求都会拿到旧数据（最终一致而非强一致）。
     * 适合"宁可短暂脏、不能被压垮"的热点活动数据场景。
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type,
                                            Function<ID, R> dbFallback, long ttl, TimeUnit unit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 未命中：逻辑过期方案依赖预热，不现场查库（避免未预热时大量冷数据打进数据库）
        if (json == null) {
            return null;
        }

        // 反序列化包装对象，取出真实数据与逻辑过期时间
        RedisData redisData = JSON.parseObject(json, RedisData.class);
        R r = JSON.parseObject(JSON.toJSONString(redisData.getData()), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 未到逻辑过期时间：直接返回
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }

        // 已过期：抢到锁的线程负责异步重建，没抢到的直接返回旧值
        String lockKey = "lock:" + key;
        if (tryLock(lockKey)) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R rNew = dbFallback.apply(id);
                    RedisData wrapper = new RedisData();
                    wrapper.setData(rNew);
                    wrapper.setExpireTime(LocalDateTime.now().plus(Duration.ofMillis(unit.toMillis(ttl))));
                    // 逻辑过期数据不设 Redis TTL，靠 expireTime 字段自行判断
                    stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(wrapper));
                    log.info("缓存逻辑过期重建完成, key={}", key);
                } catch (Exception e) {
                    log.error("缓存逻辑过期重建失败, key={}", key, e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        // 无论是否触发重建，都先返回旧数据（高可用优先，容忍短暂脏数据）
        return r;
    }

    /**
     * 精确删除一个缓存 key（管理端单条数据变更后清缓存）
     */
    public void evict(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 按前缀批量删除缓存（管理端批量操作、无法定位具体分类时清缓存）。
     * 用 SCAN 游标代替课程原版的 KEYS 命令：KEYS 会一次性遍历全部 key 并阻塞 Redis 主线程，
     * 生产环境 key 量大时是事故级操作；SCAN 分批游出、不阻塞。
     */
    public void evictByPrefix(String prefix) {
        try (Cursor<String> cursor = stringRedisTemplate.scan(
                ScanOptions.scanOptions().match(prefix + "*").count(1000).build())) {
            List<String> keys = new ArrayList<>();
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
            if (!keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        }
    }

    /** 互斥锁：setIfAbsent(key, value, ttl) 是 Redis 的 SET NX PX，原子操作 */
    private boolean tryLock(String key) {
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
