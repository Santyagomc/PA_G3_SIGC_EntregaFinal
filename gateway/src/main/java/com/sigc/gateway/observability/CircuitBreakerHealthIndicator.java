package com.sigc.gateway.observability;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

/** Expone en /actuator/health el estado del circuit breaker hacia la API REST. */
@Component("apiRest")
public class CircuitBreakerHealthIndicator implements HealthIndicator {

	private final CircuitBreaker circuitBreaker;

	public CircuitBreakerHealthIndicator(CircuitBreaker apiCircuitBreaker) {
		this.circuitBreaker = apiCircuitBreaker;
	}

	@Override
	public Health health() {
		CircuitBreaker.State state = circuitBreaker.getState();
		var metrics = circuitBreaker.getMetrics();
		Health.Builder b = (state == CircuitBreaker.State.OPEN) ? Health.outOfService() : Health.up();
		return b.withDetail("circuitBreaker", state.name())
				.withDetail("tasaFallos", metrics.getFailureRate())
				.withDetail("llamadasFallidas", metrics.getNumberOfFailedCalls())
				.withDetail("llamadasRechazadas", metrics.getNumberOfNotPermittedCalls())
				.build();
	}
}
