package com.sigc.gateway.config;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Configuration;

/**
 * Caché Redis (TTL en spring.cache.redis.time-to-live). Si Redis falla, el error se registra y la
 * consulta continúa contra la API: la caché no debe convertirse en un punto único de fallo.
 */
@Configuration
public class CacheConfig implements CachingConfigurer {

	@Override
	public CacheErrorHandler errorHandler() {
		return new LoggingCacheErrorHandler();
	}
}
