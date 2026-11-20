package com.sigc.gateway.client;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.sigc.gateway.observability.CorrelationIdFilter;
import com.sigc.gateway.security.CurrentUser;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;

/**
 * Cliente HTTP interno hacia la API REST (sigc-api). Única puerta de salida del gateway.
 * - Propaga solo Authorization (la API vuelve a validar el token) y X-Correlation-Id.
 * - Aplica timeout (en el RestClient), circuit breaker a todas las llamadas y retry solo a los GET.
 * - Traduce las respuestas de error de la API a {@link ApiClientException} con su "mensaje".
 */
@Component
public class ApiClient {

	private static final Logger log = LoggerFactory.getLogger(ApiClient.class);

	private final RestClient restClient;
	private final CircuitBreaker circuitBreaker;
	private final Retry retry;
	private final ObjectMapper mapper = new ObjectMapper();

	public ApiClient(RestClient apiRestClient, CircuitBreaker apiCircuitBreaker, Retry apiRetry) {
		this.restClient = apiRestClient;
		this.circuitBreaker = apiCircuitBreaker;
		this.retry = apiRetry;
	}

	public CircuitBreaker getCircuitBreaker() {
		return circuitBreaker;
	}

	/** Consulta (idempotente): circuit breaker + retry. */
	public JsonNode get(String path, Map<String, ?> params) {
		return execute(HttpMethod.GET, path, () -> call(HttpMethod.GET, path, params, null, null), true);
	}

	/** Operación que cambia estado con cuerpo JSON: circuit breaker, sin retry. */
	public JsonNode postJson(String path, Object body) {
		return execute(HttpMethod.POST, path,
				() -> call(HttpMethod.POST, path, Map.of(), body, MediaType.APPLICATION_JSON), false);
	}

	/** PUT con parámetros de consulta: circuit breaker, sin retry. */
	public JsonNode put(String path, Map<String, ?> params) {
		return execute(HttpMethod.PUT, path, () -> call(HttpMethod.PUT, path, params, null, null), false);
	}

	/** POST multipart (radicación con adjuntos): circuit breaker, sin retry. */
	public JsonNode postMultipart(String path, MultiValueMap<String, Object> parts) {
		return execute(HttpMethod.POST, path,
				() -> call(HttpMethod.POST, path, Map.of(), parts, MediaType.MULTIPART_FORM_DATA), false);
	}

	private JsonNode execute(HttpMethod method, String path, Supplier<JsonNode> rawCall, boolean retryable) {
		Supplier<JsonNode> protectedCall = () -> {
			try {
				return circuitBreaker.executeSupplier(rawCall);
			} catch (CallNotPermittedException e) {
				log.warn("[RESILIENCIA] circuit breaker ABIERTO: se evita llamar {} {}", method, path);
				throw new ApiUnavailableException("La API REST no está disponible temporalmente (circuito abierto).", e, true);
			}
		};
		return retryable ? retry.executeSupplier(protectedCall) : protectedCall.get();
	}

	private JsonNode call(HttpMethod method, String path, Map<String, ?> params, Object body, MediaType contentType) {
		long start = System.currentTimeMillis();
		Raw raw;
		try {
			RestClient.RequestBodySpec spec = restClient.method(method).uri(b -> {
				b.path(path);
				params.forEach((k, v) -> {
					if (v != null) {
						b.queryParam(k, v);
					}
				});
				return b.build();
			}).headers(this::propagateHeaders);
			if (body != null) {
				spec = spec.contentType(contentType).body(body instanceof String || body instanceof MultiValueMap<?, ?>
						? body : toJson(body));
			}
			raw = spec.exchange((req, res) -> new Raw(res.getStatusCode().value(),
					StreamUtils.copyToString(res.getBody(), StandardCharsets.UTF_8)), true);
		} catch (RestClientException e) {
			log.warn("[API-CLIENT] {} {} -> sin respuesta ({} ms): {}", method, path,
					System.currentTimeMillis() - start, e.getClass().getSimpleName());
			throw new ApiUnavailableException("La API REST no respondió a tiempo o rechazó la conexión.", e, false);
		}
		log.info("[API-CLIENT] {} {} -> {} ({} ms)", method, path, raw.status(), System.currentTimeMillis() - start);
		JsonNode json = parse(raw.body());
		if (raw.status() >= 400) {
			String mensaje = json.path("mensaje").asText(null);
			throw new ApiClientException(raw.status(), mensaje != null ? mensaje : "La API REST respondió " + raw.status());
		}
		return json;
	}

	private void propagateHeaders(HttpHeaders headers) {
		String token = CurrentUser.bearerToken();
		if (token != null) {
			headers.setBearerAuth(token);
		}
		String correlationId = CorrelationIdFilter.current();
		if (!correlationId.isEmpty()) {
			headers.set(CorrelationIdFilter.HEADER, correlationId);
		}
		headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
	}

	private String toJson(Object body) {
		try {
			return mapper.writeValueAsString(body);
		} catch (Exception e) {
			throw new IllegalArgumentException("No se pudo serializar la petición", e);
		}
	}

	private JsonNode parse(String body) {
		if (body == null || body.isBlank()) {
			return MissingNode.getInstance();
		}
		try {
			return mapper.readTree(body);
		} catch (Exception e) {
			return MissingNode.getInstance();
		}
	}

	private record Raw(int status, String body) {}
}
