package com.backend.sigcapi.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.backend.sigcapi.entities.FailedEvent;
import com.backend.sigcapi.entities.OutboxEvent;
import com.backend.sigcapi.enums.OutboxStatus;
import com.backend.sigcapi.enums.RequestEventType;
import com.backend.sigcapi.messaging.consumer.ConsumerNames;
import com.backend.sigcapi.repositories.FailedEventRepository;
import com.backend.sigcapi.repositories.NotificationRepository;
import com.backend.sigcapi.repositories.OutboxEventRepository;
import com.backend.sigcapi.repositories.ProcessedEventRepository;
import com.backend.sigcapi.repositories.RequestHistoryRepository;
import com.backend.sigcapi.repositories.RequestRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * Prueba de integración EXTREMO A EXTREMO del flujo asíncrono con un broker Kafka real
 * embebido (KRaft): API REST → outbox → relay (productor) → tópico audit-events →
 * consumidores sigc-audit y sigc-notifications → MariaDB/H2; y además idempotencia ante un
 * duplicado y desvío al DLT de mensajes inválidos.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
@EmbeddedKafka(partitions = 3, topics = { "audit-events", "audit-events.DLT" },
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sigcapi_kafka;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "app.kafka.listeners.auto-startup=true",
        "app.kafka.outbox.relay-enabled=true",
        "app.kafka.outbox.relay-interval-ms=200",
        "app.kafka.outbox.relay-initial-delay-ms=0",
        "app.kafka.retry.max-retries=1",
        "app.kafka.retry.initial-interval-ms=100"
})
class KafkaEventFlowIntegrationTest {

    private static final String ADMIN_DOCUMENT = "1000000000";
    private static final String ADMIN_PASSWORD = "Admin.12345";
    private static final long TIMEOUT_MS = 45_000;

    @Autowired private WebApplicationContext context;
    @Autowired private OutboxEventRepository outboxRepository;
    @Autowired private ProcessedEventRepository processedRepository;
    @Autowired private RequestHistoryRepository historyRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private FailedEventRepository failedRepository;
    @Autowired private RequestRepository requestRepository;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void flujoCompletoProductorConsumidoresIdempotenciaYDlt() throws Exception {
        String token = "Bearer " + login();

        // 1) Radicar una PQR (HTTP 201). La respuesta NO espera a Kafka.
        long requestId = radicar(token);

        // 2) Cambiar su estado a CERRADO (HTTP 200).
        mvc.perform(put("/requests/{id}/status", requestId).param("status", "CERRADO")
                .param("observations", "Respuesta de fondo enviada").header("Authorization", token))
                .andExpect(status().isOk());

        // 3) El relay publica ambos eventos (PUBLISHED con partición y offset)...
        await("2 eventos PUBLISHED en el outbox",
                () -> outboxRepository.countByAggregateIdAndStatus(requestId, OutboxStatus.PUBLISHED) == 2);
        List<OutboxEvent> eventos = outboxRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(requestId)).toList();
        eventos.forEach(e -> {
            assertNotNull(e.getKafkaPartition());
            assertNotNull(e.getKafkaOffset());
        });
        // ...ambos con la misma clave ⇒ misma partición ⇒ orden garantizado
        assertEquals(1, eventos.stream().map(OutboxEvent::getKafkaPartition).distinct().count());

        // 4) Consumidor de auditoría: 2 filas en request_history (radicación + cierre)
        await("2 registros de historial", () -> historyRepository.countByRequestId(requestId) == 2);

        // 5) Consumidor de notificaciones: acuse + aviso de cierre, y mail_status = true
        await("2 notificaciones", () -> notificationRepository.countByRequestId(requestId) == 2);
        await("mail_status actualizado",
                () -> Boolean.TRUE.equals(requestRepository.findById(requestId).orElseThrow().getMailStatus()));

        // 6) El historial es consultable por la API (resultado verificable)
        mvc.perform(get("/requests/{id}/history", requestId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].statusNew").value("CREADO"))
                .andExpect(jsonPath("$.data.content[1].statusOld").value("CREADO"))
                .andExpect(jsonPath("$.data.content[1].statusNew").value("CERRADO"));

        // 7) IDEMPOTENCIA: se reenvía byte a byte el evento de cierre...
        OutboxEvent cierre = eventos.stream()
                .filter(e -> e.getEventType() == RequestEventType.REQUEST_STATUS_CHANGED).findFirst().orElseThrow();
        mvc.perform(post("/events/simulate/duplicate/{eventId}", cierre.getEventId()).header("Authorization", token))
                .andExpect(status().isAccepted());

        // ...y luego un cambio real (reapertura) con la misma clave ⇒ misma partición, procesado DESPUÉS del duplicado
        mvc.perform(put("/requests/{id}/status", requestId).param("status", "CREADO")
                .param("observations", "Reapertura").header("Authorization", token))
                .andExpect(status().isOk());
        await("historial con la reapertura", () -> historyRepository.countByRequestId(requestId) == 3);
        await("reapertura procesada por notificaciones", () -> processedRepository.countByConsumer(ConsumerNames.NOTIFICATIONS) == 3);

        assertEquals(3, historyRepository.countByRequestId(requestId), "el duplicado NO generó historial");
        assertEquals(2, notificationRepository.countByRequestId(requestId), "el duplicado NO generó un segundo correo");
        assertEquals(1, processedRepository.countByEventIdAndConsumer(cierre.getEventId(), ConsumerNames.AUDIT));
        assertEquals(1, processedRepository.countByEventIdAndConsumer(cierre.getEventId(), ConsumerNames.NOTIFICATIONS));

        // 8) ERRORES: tres mensajes que no se pueden procesar terminan en el DLT (uno por grupo consumidor)
        mvc.perform(post("/events/simulate/invalid-payload").header("Authorization", token)).andExpect(status().isAccepted());
        mvc.perform(post("/events/simulate/contract-violation").header("Authorization", token)).andExpect(status().isAccepted());
        mvc.perform(post("/events/simulate/unknown-request").header("Authorization", token)).andExpect(status().isAccepted());

        await("6 mensajes en failed_events (3 escenarios x 2 grupos)", () -> failedRepository.count() >= 6);
        List<FailedEvent> fallidos = failedRepository.findAll();
        assertTrue(fallidos.stream().allMatch(f -> "audit-events".equals(f.getOriginalTopic())));
        assertTrue(fallidos.stream().anyMatch(f -> f.getErrorMessage() != null && f.getErrorMessage().contains("no existe")));
        assertTrue(fallidos.stream().anyMatch(f -> f.getErrorMessage() != null && f.getErrorMessage().contains("JSON inválido")));

        // 9) El flujo normal sigue funcionando después de los mensajes veneno (la partición no se bloqueó)
        long otra = radicar(token);
        await("PQR posterior auditada", () -> historyRepository.countByRequestId(otra) == 1);

        // 10) Resumen consultable por API
        mvc.perform(get("/events/summary").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outbox.FAILED").value(0));
    }

    // ------------------------------------------------------------------ utilidades

    private String login() throws Exception {
        String body = mvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(ADMIN_DOCUMENT, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.token");
    }

    private long radicar(String token) throws Exception {
        String requestJson = """
                {"title":"Demora en respuesta","description":"No he recibido respuesta a mi petición",
                 "fullName":"Santiago Murcia","email":"ciudadano.kafka@example.com","document":"1075000001",
                 "city":"Neiva","phone":"3001234567","type":"QUEJA","acceptedDataPolicy":true,
                 "expirationDate":"2030-12-31"}""";
        String body = mvc.perform(multipart("/requests/save")
                .file(new MockMultipartFile("request", "", "application/json", requestJson.getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("files", "soporte.txt", "text/plain", "soporte".getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Number id = JsonPath.read(body, "$.data.id");
        return id.longValue();
    }

    private static void await(String description, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        fail("Tiempo agotado esperando: " + description);
    }
}
