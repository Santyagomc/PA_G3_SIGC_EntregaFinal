package com.backend.sigcapi.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.backend.sigcapi.messaging.event.EventHeaders;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Observabilidad (Semana 14): asigna a cada petición un identificador de correlación.
 * Si la petición llega desde el gateway GraphQL se reutiliza su {@code X-Correlation-Id};
 * si no, se genera uno nuevo. Se guarda en el MDC (aparece en cada línea de log), se devuelve
 * en la respuesta y viaja hasta Kafka (outbox → header {@code x-correlation-id} → consumidores).
 * No es un identificador de negocio: solo relaciona los logs de un mismo flujo.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    /** Evita inyección en logs: solo se aceptan identificadores cortos y alfanuméricos. */
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String correlationId = (incoming != null && SAFE.matcher(incoming).matches())
                ? incoming
                : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** Lee el identificador de correlación de los headers de un mensaje Kafka (o "-" si no viene). */
    public static String fromRecord(ConsumerRecord<?, ?> record) {
        if (record == null || record.headers() == null) {
            return "-";
        }
        Header header = record.headers().lastHeader(EventHeaders.CORRELATION_ID);
        return header == null ? "-" : new String(header.value(), StandardCharsets.UTF_8);
    }
}
