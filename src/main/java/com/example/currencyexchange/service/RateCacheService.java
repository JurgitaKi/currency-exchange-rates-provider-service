package com.example.currencyexchange.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Two-layer cache for exchange rates:
 * <ol>
 *   <li>Primary: JVM {@link ConcurrentHashMap} – zero-latency access within the process.</li>
 *   <li>Secondary (optional): Redis – survives restarts and is shared across instances.</li>
 * </ol>
 *
 * <p>Key format: {@code "FROM_TO"} (e.g. {@code "EUR_USD"}).
 */
@Slf4j
@Service
public class RateCacheService {

    private static final String REDIS_KEY_PREFIX = "rate:";

    private final ConcurrentMap<String, BigDecimal> localCache = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Stores a rate in the local map and, if Redis is available, also in Redis.
     */
    public void put(String from, String to, BigDecimal rate) {
        String key = buildKey(from, to);
        localCache.put(key, rate);
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + key, rate);
            } catch (Exception ex) {
                log.warn("Redis write failed for key '{}', falling back to local cache only: {}", key, ex.getMessage());
            }
        }
    }

    /**
     * Returns the cached rate, checking local map first, then Redis.
     */
    public Optional<BigDecimal> get(String from, String to) {
        String key = buildKey(from, to);
        BigDecimal local = localCache.get(key);
        if (local != null) {
            return Optional.of(local);
        }
        if (redisTemplate != null) {
            try {
                Object redisValue = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + key);
                if (redisValue instanceof BigDecimal bd) {
                    localCache.put(key, bd);
                    return Optional.of(bd);
                }
                if (redisValue instanceof Number n) {
                    BigDecimal bd = new BigDecimal(n.toString());
                    localCache.put(key, bd);
                    return Optional.of(bd);
                }
            } catch (Exception ex) {
                log.warn("Redis read failed for key '{}': {}", key, ex.getMessage());
            }
        }
        return Optional.empty();
    }

    /**
     * Clears the local map. Called before refreshing rates so stale entries are removed.
     */
    public void evictAll() {
        localCache.clear();
        log.debug("Local rate cache evicted");
    }

    private String buildKey(String from, String to) {
        return from.toUpperCase() + "_" + to.toUpperCase();
    }
}
