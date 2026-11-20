package com.sigc.gateway.graphql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigc.gateway.graphql.Modelos.ResumenIndicadores;

/**
 * Guarda en Redis (sin TTL) el último resumen de indicadores calculado con éxito. Si Redis no está
 * disponible, solo se registra la advertencia: la caché es una optimización, nunca un punto de fallo.
 */
@Component
public class UltimoValorStore {

	private static final Logger log = LoggerFactory.getLogger(UltimoValorStore.class);
	static final String KEY = "sigc:gateway:indicadores:ultimo";

	private final ObjectProvider<StringRedisTemplate> redis;
	private final boolean enabled;
	private final ObjectMapper mapper = new ObjectMapper();

	public UltimoValorStore(ObjectProvider<StringRedisTemplate> redis,
			@Value("${app.cache.stale-fallback-enabled:true}") boolean enabled) {
		this.redis = redis;
		this.enabled = enabled;
	}

	public void guardar(ResumenIndicadores r) {
		if (!enabled) {
			return;
		}
		try {
			StringRedisTemplate t = redis.getIfAvailable();
			if (t != null) {
				t.opsForValue().set(KEY, mapper.writeValueAsString(r));
			}
		} catch (Exception e) {
			log.warn("[CACHE] no se pudo guardar el respaldo en Redis: {}", e.getClass().getSimpleName());
		}
	}

	public ResumenIndicadores leer() {
		if (!enabled) {
			return null;
		}
		try {
			StringRedisTemplate t = redis.getIfAvailable();
			String json = t != null ? t.opsForValue().get(KEY) : null;
			return json != null ? mapper.readValue(json, ResumenIndicadores.class) : null;
		} catch (Exception e) {
			log.warn("[CACHE] no se pudo leer el respaldo de Redis: {}", e.getClass().getSimpleName());
			return null;
		}
	}
}
