package com.seongmin.spike.error.infrastructure;

import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** 1초 버킷 슬라이딩 윈도우(근사). 키 ec:{policyId}:{fpKey} = HASH(epochSecond -> count). */
@Component
public class RedisErrorCounter implements ErrorCounter {
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script = new DefaultRedisScript<>();

    public RedisErrorCounter(StringRedisTemplate redis) {
        this.redis = redis;
        script.setLocation(new ClassPathResource("redis/counter.lua"));
        script.setResultType(Long.class);
    }

    public static String key(CounterRequest r) { return "ec:" + r.policyId() + ":" + r.fpKey(); }

    @Override
    public CountResult increment(CounterRequest r) {
        Long total = redis.execute(script, List.of(key(r)),
                String.valueOf(r.at().getEpochSecond()),
                String.valueOf(r.windowSeconds()),
                String.valueOf(r.windowSeconds() + 5));
        return new CountResult(total == null ? 0 : total, DetectionPath.REDIS);
    }
}
