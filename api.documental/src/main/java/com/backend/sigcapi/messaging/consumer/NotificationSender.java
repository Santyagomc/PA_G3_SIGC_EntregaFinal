package com.backend.sigcapi.messaging.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.backend.sigcapi.enums.NotificationStatus;

/**
 * Canal de salida de notificaciones. Con {@code MAIL_ENABLED=true} envía por SMTP; si no,
 * opera en modo SIMULADO (registra el mensaje sin enviarlo) para poder demostrar el flujo sin
 * credenciales de correo. Un fallo SMTP lanza excepción ⇒ el mensaje Kafka se reintenta.
 */
@Component
public class NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final boolean mailEnabled;
    private final String from;

    public NotificationSender(ObjectProvider<JavaMailSender> mailSender,
            @Value("${app.notifications.mail-enabled:false}") boolean mailEnabled,
            @Value("${app.notifications.from:}") String from) {
        this.mailSender = mailSender;
        this.mailEnabled = mailEnabled;
        this.from = from;
    }

    public NotificationStatus send(String to, String subject, String body) {
        if (!mailEnabled) {
            log.info("[NOTIFICACION-SIMULADA] para={} asunto=\"{}\" (MAIL_ENABLED=false)", mask(to), subject);
            return NotificationStatus.SIMULATED;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("MAIL_ENABLED=true pero no hay JavaMailSender configurado");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        if (from != null && !from.isBlank()) {
            message.setFrom(from);
        }
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message); // MailException => reintento con backoff y, si persiste, DLT
        log.info("[NOTIFICACION] Correo enviado para={} asunto=\"{}\"", mask(to), subject);
        return NotificationStatus.SENT;
    }

    /** Enmascara el correo en los logs (Ley 1581): santiago@x.com -> s******o@x.com */
    static String mask(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        String user = email.substring(0, at);
        if (user.length() <= 2) {
            return "*".repeat(user.length()) + email.substring(at);
        }
        return user.charAt(0) + "*".repeat(user.length() - 2) + user.charAt(user.length() - 1) + email.substring(at);
    }
}
