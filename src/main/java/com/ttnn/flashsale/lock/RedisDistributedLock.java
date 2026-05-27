package com.ttnn.flashsale.lock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 【阶段三 · Redis 分布式锁】
 * <p>
 * 基于 Redis SETNX + Lua 脚本实现的轻量级分布式锁。<br/>
 * 加锁：SETNX 带过期时间，防止死锁。<br/>
 * 解锁：Lua 脚本原子性地「比较 value → 删除 key」，防止误删他人持有的锁。
 */
@Component
@Slf4j
public class RedisDistributedLock {

    /**
     * 解锁 Lua 脚本：只有持有者（value 匹配）才能删除锁
     */
    private static final String UNLOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else " +
            "  return 0 " +
            "end";

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 尝试获取锁
     *
     * @param key     锁的 key
     * @param value   锁的 value（通常为 UUID，用于标识持有者）
     * @param timeout 锁的过期时间
     * @param unit    时间单位
     * @return true=获取成功
     */
    public boolean tryLock(String key, String value, long timeout, TimeUnit unit) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
        boolean locked = Boolean.TRUE.equals(result);
        if (locked) {
            log.debug("获取分布式锁成功: key={}", key);
        } else {
            log.debug("获取分布式锁失败: key={}", key);
        }
        return locked;
    }

    /**
     * 释放锁（Lua 脚本保证原子性）
     */
    public void unlock(String key, String value) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_LUA, Long.class);
        Long result = redisTemplate.execute(script, Collections.singletonList(key), value);
        if (result != null && result > 0) {
            log.debug("释放分布式锁成功: key={}", key);
        }
    }
}
