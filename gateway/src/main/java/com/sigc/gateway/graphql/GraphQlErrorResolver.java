package com.sigc.gateway.graphql;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import com.sigc.gateway.client.ApiClientException;
import com.sigc.gateway.client.ApiUnavailableException;
import com.sigc.gateway.observability.CorrelationIdFilter;
import com.sigc.gateway.security.CurrentUser;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;

/**
 * Errores controlados: cada excepción se traduce a un error GraphQL con extensiones
 * {@code codigo} (BAD_REQUEST, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, UNAVAILABLE, INTERNAL_ERROR)
 * y {@code correlationId}, sin exponer trazas ni detalles internos de la arquitectura.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GraphQlErrorResolver extends DataFetcherExceptionResolverAdapter {

	private static final Logger log = LoggerFactory.getLogger(GraphQlErrorResolver.class);

	public GraphQlErrorResolver() {
		setThreadLocalContextAware(true);
	}

	@Override
	protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
		if (ex instanceof ApiClientException api) {
			return switch (api.getStatus()) {
				case 400, 409, 413 -> build(env, ErrorType.BAD_REQUEST, "BAD_REQUEST", api.getMessage());
				case 401 -> build(env, ErrorType.UNAUTHORIZED, "UNAUTHORIZED", api.getMessage());
				case 403 -> build(env, ErrorType.FORBIDDEN, "FORBIDDEN", api.getMessage());
				case 404 -> build(env, ErrorType.NOT_FOUND, "NOT_FOUND", api.getMessage());
				default -> {
					log.error("[GATEWAY] la API respondió {} en {}", api.getStatus(), env.getField().getName());
					yield build(env, ErrorType.INTERNAL_ERROR, "UNAVAILABLE",
							"El servicio de solicitudes presentó un error. Intente más tarde.");
				}
			};
		}
		if (ex instanceof ApiUnavailableException u) {
			return build(env, ErrorType.INTERNAL_ERROR, "UNAVAILABLE", u.isCircuitOpen()
					? "El servicio de solicitudes no está disponible temporalmente. Intente en unos segundos."
					: "El servicio de solicitudes no respondió a tiempo. Intente nuevamente.");
		}
		if (ex instanceof AuthenticationException || (ex instanceof AccessDeniedException && !CurrentUser.isAuthenticated())) {
			return build(env, ErrorType.UNAUTHORIZED, "UNAUTHORIZED", "Debe autenticarse (Authorization: Bearer <token>).");
		}
		if (ex instanceof AccessDeniedException) {
			return build(env, ErrorType.FORBIDDEN, "FORBIDDEN", "No tiene permisos para realizar esta operación.");
		}
		if (ex instanceof IllegalArgumentException) {
			return build(env, ErrorType.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
		}
		log.error("[GATEWAY] error inesperado en {}", env.getField().getName(), ex);
		return build(env, ErrorType.INTERNAL_ERROR, "INTERNAL_ERROR", "Error inesperado en el gateway.");
	}

	private GraphQLError build(DataFetchingEnvironment env, ErrorType type, String codigo, String message) {
		Map<String, Object> ext = new LinkedHashMap<>();
		ext.put("codigo", codigo);
		ext.put("correlationId", CorrelationIdFilter.current());
		return GraphqlErrorBuilder.newError(env).errorType(type).message(message).extensions(ext).build();
	}
}
