package com.backend.sigcapi.messaging.producer;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/**
 * Envío síncrono-confirmado a Kafka: espera el ack del broker (acks=all + idempotencia del
 * productor configurados en application.properties) para conocer partición y offset.
 */
@Component
public class KafkaEventSender {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final long sendTimeoutMs;

    public KafkaEventSender(KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.kafka.producer.send-timeout-ms:10000}") long sendTimeoutMs) {
        this.kafkaTemplate = kafkaTemplate;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    /** Resultado mínimo de un envío confirmado. */
    public record Delivery(String topic, int partition, long offset) {
    }

    public Delivery send(String topic, String key, String value, Map<String, String> headers)
            throws InterruptedException, ExecutionException, TimeoutException {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        if (headers != null) {
            headers.forEach((name, val) -> {
                if (val != null) {
                    record.headers().add(name, val.getBytes(StandardCharsets.UTF_8));
                }
            });
        }
        SendResult<String, String> result = kafkaTemplate.send(record).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
        RecordMetadata metadata = result.getRecordMetadata();
        return new Delivery(metadata.topic(), metadata.partition(), metadata.offset());
    }
}
