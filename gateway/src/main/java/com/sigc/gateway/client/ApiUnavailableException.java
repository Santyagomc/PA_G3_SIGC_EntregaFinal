package com.sigc.gateway.client;

/** La API REST no respondió a tiempo, rechazó la conexión o el circuit breaker está abierto. */
public class ApiUnavailableException extends RuntimeException {

	private final boolean circuitOpen;

	public ApiUnavailableException(String message, Throwable cause, boolean circuitOpen) {
		super(message, cause);
		this.circuitOpen = circuitOpen;
	}

	public boolean isCircuitOpen() {
		return circuitOpen;
	}
}
