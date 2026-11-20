package com.sigc.gateway.client;

/** La API REST respondió con un código de error HTTP (4xx/5xx). Lleva el mensaje de negocio de la API. */
public class ApiClientException extends RuntimeException {

	private final int status;

	public ApiClientException(int status, String message) {
		super(message);
		this.status = status;
	}

	public int getStatus() {
		return status;
	}

	public boolean isServerError() {
		return status >= 500;
	}
}
