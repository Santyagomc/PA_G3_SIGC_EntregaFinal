package com.backend.sigcapi.messaging.consumer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import com.backend.sigcapi.entities.FailedEvent;
import com.backend.sigcapi.repositories.FailedEventRepository;

/**
 * Consumidor 3 — grupo {@code sigc-dlt}: persiste en {@code failed_events} cada mensaje del
 * Dead Letter Topic con su causa y su origen (tópico, partición, offset, grupo), para que un
 * administrador lo revise en {@code GET /events/dead-letters}.
 */
@Component
public class DeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterConsumer.class);

    private final FailedEventRepository repository;

    public DeadLetterConsumer(FailedEventRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(id = "deadLetterListener",
            topics = "${app.kafka.topics.request-events-dlt:audit-events.DLT}",
            groupId = "${app.kafka.consumers.dlt-group:sigc-dlt}",
            containerFactory = "deadLetterListenerContainerFactory",
            autoStartup = "${app.kafka.listeners.auto-startup:true}")
    public void onDeadLetter(ConsumerRecord<String, String> record) {
        FailedEvent failed = new FailedEvent();
        failed.setOriginalTopic(headerString(record, KafkaHeaders.DLT_ORIGINAL_TOPIC, record.topic()));
        failed.setOriginalPartition(headerInt(record, KafkaHeaders.DLT_ORIGINAL_PARTITION));
        failed.setOriginalOffset(headerLong(record, KafkaHeaders.DLT_ORIGINAL_OFFSET));
        failed.setConsumerGroup(headerString(record, KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP, null));
        failed.setEventKey(truncate(record.key(), 64));
        failed.setPayload(record.value());
        failed.setExceptionClass(truncate(exceptionClass(record), 255));
        failed.setErrorMessage(truncate(errorMessage(record), 1000));
        repository.save(failed);

        log.error("[KAFKA-DLT] Mensaje fallido almacenado id={} origen={}-{}@{} grupo={} causa={} mensaje={}",
                failed.getId(), failed.getOriginalTopic(), failed.getOriginalPartition(), failed.getOriginalOffset(),
                failed.getConsumerGroup(), failed.getExceptionClass(), failed.getErrorMessage());
    }

    /**
     * Clase de la excepción que causó el envío al DLT. Si Spring Kafka la reporta envuelta en
     * ListenerExecutionFailedException, se usa la causa (la excepción real del consumidor).
     */
    private static String exceptionClass(ConsumerRecord<?, ?> record) {
        String fqcn = headerString(record, KafkaHeaders.DLT_EXCEPTION_FQCN, null);
        String cause = headerString(record, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN, null);
        if (fqcn == null || (fqcn.endsWith("ListenerExecutionFailedException") && cause != null)) {
            return cause;
        }
        return fqcn;
    }

    /**
     * Mensaje de error legible: si la traza incluye una causa propia de SIGC
     * ("Caused by: com.backend.sigcapi..."), se usa esa línea; si no, el mensaje del header.
     */
    private static String errorMessage(ConsumerRecord<?, ?> record) {
        String message = headerString(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE, null);
        String stack = headerString(record, KafkaHeaders.DLT_EXCEPTION_STACKTRACE, null);
        if (stack != null) {
            String prefix = "Caused by: com.backend.sigcapi";
            String cause = stack.lines()
                    .map(String::trim)
                    .filter(line -> line.startsWith(prefix))
                    .findFirst()
                    .map(line -> line.substring("Caused by: ".length()))
                    .orElse(null);
            if (cause != null) {
                return cause;
            }
        }
        return message;
    }

    private static String headerString(ConsumerRecord<?, ?> record, String name, String fallback) {
        Header header = record.headers().lastHeader(name);
        return header == null || header.value() == null ? fallback : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static Integer headerInt(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null || header.value() == null) {
            return null;
        }
        byte[] v = header.value();
        return v.length == Integer.BYTES ? ByteBuffer.wrap(v).getInt() : parseIntOrNull(new String(v, StandardCharsets.UTF_8));
    }

    private static Long headerLong(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null || header.value() == null) {
            return null;
        }
        byte[] v = header.value();
        return v.length == Long.BYTES ? ByteBuffer.wrap(v).getLong() : parseLongOrNull(new String(v, StandardCharsets.UTF_8));
    }

    private static Integer parseIntOrNull(String s) {
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long parseLongOrNull(String s) {
        try {
            return Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
