package com.backend.sigcapi.messaging.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;

import com.backend.sigcapi.messaging.event.InvalidEventException;
import com.backend.sigcapi.messaging.event.NonRetryableEventException;

/**
 * Infraestructura Kafka de SIGC.
 *
 * <ul>
 *   <li><b>audit-events</b>: eventos de dominio de las PQR (3 particiones, clave = requestId).</li>
 *   <li><b>audit-events.DLT</b>: mensajes que no se pudieron procesar (mismas particiones).</li>
 * </ul>
 * Los tópicos también los crea el servicio {@code kafka-init} del Docker Compose; declararlos
 * aquí como {@link NewTopic} hace que la API sea autosuficiente (idempotente si ya existen).
 */
@Configuration
@EnableScheduling
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    @Value("${app.kafka.topics.request-events:audit-events}")
    private String requestEventsTopic;

    @Value("${app.kafka.topics.request-events-dlt:audit-events.DLT}")
    private String deadLetterTopic;

    @Value("${app.kafka.topics.partitions:3}")
    private int partitions;

    @Value("${app.kafka.topics.replicas:1}")
    private int replicas;

    @Value("${app.kafka.topics.retention-ms:604800000}")
    private String retentionMs;

    @Value("${app.kafka.topics.dlt-retention-ms:2592000000}")
    private String dltRetentionMs;

    @Value("${app.kafka.retry.max-retries:3}")
    private int maxRetries;

    @Value("${app.kafka.retry.initial-interval-ms:1000}")
    private long initialIntervalMs;

    @Value("${app.kafka.retry.multiplier:2.0}")
    private double multiplier;

    @Value("${app.kafka.retry.max-interval-ms:10000}")
    private long maxIntervalMs;

    @Bean
    public NewTopic requestEventsTopic() {
        return TopicBuilder.name(requestEventsTopic)
                .partitions(partitions)
                .replicas(replicas)
                .config(TopicConfig.RETENTION_MS_CONFIG, retentionMs)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .build();
    }

    @Bean
    public NewTopic requestEventsDeadLetterTopic() {
        // Debe tener al menos las mismas particiones que el tópico origen: el recoverer
        // publica en la MISMA partición de la que vino el mensaje.
        return TopicBuilder.name(deadLetterTopic)
                .partitions(partitions)
                .replicas(replicas)
                .config(TopicConfig.RETENTION_MS_CONFIG, dltRetentionMs)
                .build();
    }

    /**
     * Manejador de errores de los consumidores de negocio (Spring Boot lo aplica a la fábrica
     * de listeners autoconfigurada al ser el único {@code CommonErrorHandler} del contexto).
     *
     * <ul>
     *   <li>Errores transitorios (BD caída, SMTP no disponible): reintentos con backoff exponencial
     *       (1 s, 2 s, 4 s por defecto) y, si persisten, envío al DLT.</li>
     *   <li>{@link InvalidEventException} y {@link NonRetryableEventException}: directo al DLT,
     *       sin reintentos (reintentar no cambia el resultado).</li>
     * </ul>
     * El offset se confirma después de publicar en el DLT, así un mensaje "veneno" nunca bloquea
     * la partición.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> {
                    log.error("[KAFKA-DLT] Enviando a {} el mensaje {}-{}@{} key={} causa={}",
                            deadLetterTopic, record.topic(), record.partition(), record.offset(), record.key(),
                            rootMessage(exception));
                    return new TopicPartition(deadLetterTopic, record.partition());
                });

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initialIntervalMs);
        backOff.setMultiplier(multiplier);
        backOff.setMaxInterval(maxIntervalMs);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(InvalidEventException.class, NonRetryableEventException.class);
        handler.setRetryListeners((record, exception, deliveryAttempt) ->
                log.warn("[KAFKA-RETRY] Falló el intento {} de {}-{}@{}: {}",
                        deliveryAttempt, record.topic(), record.partition(), record.offset(), rootMessage(exception)));
        return handler;
    }

    /**
     * Fábrica exclusiva del consumidor del DLT. Usa un manejador SIN recoverer: si guardar el
     * mensaje fallido falla de forma persistente se registra en el log, evitando el ciclo
     * "DLT → DLT".
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> deadLetterListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(2000L, 5L)));
        return factory;
    }

    static String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + ": " + current.getMessage();
    }
}
