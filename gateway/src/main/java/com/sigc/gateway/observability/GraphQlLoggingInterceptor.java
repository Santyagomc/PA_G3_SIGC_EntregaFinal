package com.sigc.gateway.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/** Registra cada operación GraphQL: nombre, resultado y duración (sin variables: pueden contener contraseñas). */
@Component
public class GraphQlLoggingInterceptor implements WebGraphQlInterceptor {

	private static final Logger log = LoggerFactory.getLogger(GraphQlLoggingInterceptor.class);

	@Override
	public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
		long start = System.currentTimeMillis();
		String operation = operationName(request.getOperationName(), request.getDocument());
		String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
		return chain.next(request).doOnNext(response -> {
			boolean restore = MDC.get(CorrelationIdFilter.MDC_KEY) == null && correlationId != null;
			if (restore) {
				MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
			}
			long ms = System.currentTimeMillis() - start;
			if (response.getErrors().isEmpty()) {
				log.info("[GATEWAY] operacion={} resultado=OK duracionMs={}", operation, ms);
			} else {
				String codes = String.join(",", response.getErrors().stream()
						.map(e -> String.valueOf(e.getExtensions().getOrDefault("codigo", e.getErrorType())))
						.distinct().toList());
				log.warn("[GATEWAY] operacion={} resultado=ERROR codigos={} duracionMs={}", operation, codes, ms);
			}
			if (restore) {
				MDC.remove(CorrelationIdFilter.MDC_KEY);
			}
		});
	}

	static String operationName(String operationName, String document) {
		if (operationName != null && !operationName.isBlank()) {
			return operationName;
		}
		String doc = document == null ? "" : document.replaceAll("\\s+", " ").trim();
		int brace = doc.indexOf('{');
		String afterBrace = brace >= 0 ? doc.substring(brace + 1).trim() : doc;
		String first = afterBrace.split("[\\s({]", 2)[0];
		String kind = doc.startsWith("mutation") ? "mutation" : "query";
		return kind + ":" + first;
	}
}
