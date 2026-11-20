package com.sigc.gateway.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.sigc.gateway.client.ApiClientException;
import com.sigc.gateway.client.ApiUnavailableException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

/**
 * Resiliencia frente a la API REST (riesgo concreto: la API puede tardar, reiniciarse o caer):
 * - Timeout: conexión 2 s y lectura 5 s (configurables). Evita hilos bloqueados indefinidamente.
 * - Retry: máx. 3 intentos con backoff exponencial (300 ms, 600 ms), SOLO para consultas GET y
 *   solo ante errores recuperables (red/timeout/5xx). Nunca para mutations (no idempotentes).
 * - Circuit breaker "api-rest": si fallan ≥50 % de las últimas 10 llamadas (mín. 5) se abre 15 s y
 *   el gateway falla rápido con UNAVAILABLE; luego deja pasar 3 llamadas de prueba (half-open).
 *   Los errores 4xx (validación, permisos, no encontrado) NO cuentan como fallos del servicio.
 */
@Configuration
public class ResilienceConfig {

	private static final Logger log = LoggerFactory.getLogger(ResilienceConfig.class);

	@Bean
	public RestClient apiRestClient(@Value("${app.api.base-url}") String baseUrl,
			@Value("${app.api.connect-timeout-ms}") long connectTimeoutMs,
			@Value("${app.api.read-timeout-ms}") long readTimeoutMs) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofMillis(connectTimeoutMs))
				.version(HttpClient.Version.HTTP_1_1)
				.build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
		return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
	}

	@Bean
	public CircuitBreaker apiCircuitBreaker(@Value("${app.resilience.cb.failure-rate:50}") float failureRate,
			@Value("${app.resilience.cb.window:10}") int window,
			@Value("${app.resilience.cb.min-calls:5}") int minCalls,
			@Value("${app.resilience.cb.open-seconds:15}") long openSeconds) {
		CircuitBreakerConfig config = CircuitBreakerConfig.custom()
				.slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
				.slidingWindowSize(window)
				.minimumNumberOfCalls(minCalls)
				.failureRateThreshold(failureRate)
				.waitDurationInOpenState(Duration.ofSeconds(openSeconds))
				.permittedNumberOfCallsInHalfOpenState(3)
				.automaticTransitionFromOpenToHalfOpenEnabled(true)
				.recordException(ResilienceConfig::isRecoverableFailure)
				.build();
		CircuitBreaker cb = CircuitBreaker.of("api-rest", config);
		cb.getEventPublisher().onStateTransition(e -> log.warn("[RESILIENCIA] circuit breaker {}: {}",
				e.getCircuitBreakerName(), e.getStateTransition()));
		return cb;
	}

	@Bean
	public Retry apiRetry(@Value("${app.resilience.retry.max-attempts:3}") int maxAttempts,
			@Value("${app.resilience.retry.initial-ms:300}") long initialMs) {
		RetryConfig config = RetryConfig.custom()
				.maxAttempts(maxAttempts)
				.intervalFunction(IntervalFunction.ofExponentialBackoff(initialMs, 2.0))
				.retryOnException(e -> isRecoverableFailure(e)
						&& !(e instanceof ApiUnavailableException u && u.isCircuitOpen()))
				.build();
		Retry retry = Retry.of("api-rest-get", config);
		retry.getEventPublisher().onRetry(e -> log.warn("[RESILIENCIA] reintento {} de la consulta a la API: {}",
				e.getNumberOfRetryAttempts(), e.getLastThrowable().getClass().getSimpleName()));
		return retry;
	}

	/** Fallos atribuibles a la disponibilidad del servicio (no a la petición del cliente). */
	public static boolean isRecoverableFailure(Throwable e) {
		return (e instanceof ApiUnavailableException u && !u.isCircuitOpen())
				|| (e instanceof ApiClientException a && a.isServerError());
	}
}
