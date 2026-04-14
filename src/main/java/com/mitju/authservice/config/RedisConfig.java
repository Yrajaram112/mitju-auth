package com.mitju.authservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration.
 *
 * Default Spring RedisTemplate uses JDK serialization — produces unreadable binary
 * keys and values. We override to use String keys and JSON values, which means
 * you can inspect the cache with redis-cli and see human-readable data.
 *
 * Current usage in auth-service:
 *   - Rate limit counters (could be added)
 *   - Token blacklist (access tokens revoked before expiry — future feature)
 *   - OTP codes (stored with TTL matching expiry)
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(mapper));
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(mapper));
        template.afterPropertiesSet();
        return template;
    }
}
