package com.backend.sigcapi.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.backend.sigcapi.messaging.event.EventHeaders;

/** Observabilidad: el identificador de correlación se reutiliza, se genera o se rechaza si es inseguro. */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void reutilizaElIdentificadorQueEnviaElGateway() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(CorrelationIdFilter.HEADER, "gw-1234567890");
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicReference<String> enMdc = new AtomicReference<>();

        filter.doFilter(req, res, (rq, rs) -> enMdc.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertThat(enMdc.get()).isEqualTo("gw-1234567890");
        assertThat(res.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("gw-1234567890");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void generaUnoNuevoSiNoVieneOEsInseguro() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(CorrelationIdFilter.HEADER, "x\nINYECCION");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (rq, rs) -> { });

        assertThat(res.getHeader(CorrelationIdFilter.HEADER)).hasSize(36).doesNotContain("INYECCION");
    }

    @Test
    void leeElIdentificadorDesdeLosHeadersDeKafka() {
        ConsumerRecord<String, String> rec = new ConsumerRecord<>("audit-events", 0, 0L, "1", "{}");
        assertThat(CorrelationIdFilter.fromRecord(rec)).isEqualTo("-");
        rec.headers().add(EventHeaders.CORRELATION_ID, "corr-abcdef12".getBytes(StandardCharsets.UTF_8));
        assertThat(CorrelationIdFilter.fromRecord(rec)).isEqualTo("corr-abcdef12");
    }
}
