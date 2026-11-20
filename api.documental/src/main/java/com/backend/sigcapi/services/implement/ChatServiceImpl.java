package com.backend.sigcapi.services.implement;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.backend.sigcapi.dto.AnthropicResponseDTO;
import com.backend.sigcapi.dto.ChatDTO;
import com.backend.sigcapi.dto.ChatSaveDTO;
import com.backend.sigcapi.entities.Chat;
import com.backend.sigcapi.entities.Rule;
import com.backend.sigcapi.entities.Session;
import com.backend.sigcapi.enums.ChatType;
import com.backend.sigcapi.mapper.SesionMapper;
import com.backend.sigcapi.repositories.ChatRepository;
import com.backend.sigcapi.repositories.RuleRepository;
import com.backend.sigcapi.repositories.SesionRepository;
import com.backend.sigcapi.services.IChatService;

import jakarta.transaction.Transactional;

@Service
public class ChatServiceImpl implements IChatService {

    private final ChatRepository repository;
    private final RuleRepository ruleRepository;
    private final SesionRepository sesionRepository;
    private final RestTemplate restTemplate;

    @Value("${anthropic.api.url}")
    private String apiUrl;

    @Value("${anthropic.api.key}")
    private String apiKey;

    public ChatServiceImpl(ChatRepository repository,
            RuleRepository ruleRepository,
            SesionRepository sesionRepository) {
        this.repository = repository;
        this.ruleRepository = ruleRepository;
        this.sesionRepository = sesionRepository;
        this.restTemplate = new RestTemplate();
    }

    @Override
    @Transactional
    public ChatDTO save(ChatSaveDTO chatDTO) {
        Session sesion = sesionRepository.findById(chatDTO.sesionId())
                .orElseThrow(() -> new RuntimeException("Sesión no encontrada"));

        if (sesion.getClosureAt() != null) {
            throw new IllegalStateException("La sesión está cerrada");
        }

        // Guardar mensaje del usuario
        Chat userChat = new Chat();
        userChat.setMensaje(chatDTO.mensaje());
        userChat.setType(chatDTO.type());
        userChat.setSesion(sesion);
        repository.save(userChat);

        // Generar y guardar respuesta de la IA
        if (chatDTO.type() == ChatType.USUARIO) {
            String aiResponse = generateResponse(chatDTO.mensaje(), chatDTO.sesionId());

            Chat assistantChat = new Chat();
            assistantChat.setMensaje(aiResponse);
            assistantChat.setType(ChatType.ASESORIA);
            assistantChat.setSesion(sesion);
            repository.save(assistantChat);

            return SesionMapper.toChatDTO(assistantChat);
        }

        return SesionMapper.toChatDTO(userChat);
    }

    @Override
    public String generateResponse(String userMessage, Long sessionId) {
        List<Rule> rules = ruleRepository.findAll();
        String contextRules = formatRulesForAI(rules);

        String systemPrompt = """
                Eres el Asistente Virtual de Servicio al Cliente de SIGCAPI. Tu misión es brindar una orientación clara, amable y profesional al ciudadano.

                INSTRUCCIONES DE INTERACCIÓN:
                1. TONO: Usa un lenguaje servicial, empático y profesional (usted). Evita tecnicismos innecesarios a menos que la regla lo exija.
                2. REGLAS DE NEGOCIO (Contexto):
                %s
                3. VERIFICACIÓN: Si el ciudadano desea radicar un trámite, actúa como un filtro de calidad; verifica paso a paso si cumple con los estándares mencionados arriba.
                4. FORMATO: Si la información contiene tablas, listas o pasos, utiliza Markdown para que la respuesta sea visualmente organizada y fácil de leer.
                5. LIMITACIONES: Si la consulta no está cubierta por las reglas anteriores, responde cordialmente: "Lo siento, en este momento no cuento con información suficiente en mi base de conocimientos para responder a esa solicitud específica. ¿Puedo ayudarte con algún otro trámite del SIGCAPI?".

                RECUERDA: Tu prioridad es que el ciudadano comprenda el proceso y se sienta acompañado en su gestión.
                """
                .formatted(contextRules);

        return callAnthropicAPI(systemPrompt, userMessage);
    }

    private String callAnthropicAPI(String system, String userMessage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        // Cuerpo de la petición
        Map<String, Object> body = Map.of(
                "model", "claude-haiku-4-5-20251001",
                "max_tokens", 1024,
                "system", system,
                "messages", List.of(Map.of("role", "user", "content", userMessage)));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            AnthropicResponseDTO response = restTemplate.postForObject(
                    apiUrl,
                    entity,
                    AnthropicResponseDTO.class);

            if (response != null && response.content() != null && !response.content().isEmpty()) {
                return response.content().get(0).text();
            }

            throw new RuntimeException("Respuesta vacía o error de la API");

        } catch (Exception e) {
            System.err.println("Error al llamar a Anthropic: " + e.getMessage());
            throw new RuntimeException("Error de comunicación con el servicio de IA: " + e.getMessage());
        }
    }

    private String formatRulesForAI(List<Rule> rules) {
        return rules.stream()
                .map(r -> String.format("- Estándar #%d (%s): %s %s. Sugerencia: %s",
                        r.getStandard() != null ? r.getStandard().getId() : 0,
                        r.getFullName(),
                        r.getOperator(),
                        r.getValue(),
                        r.getSuggestion()))
                .collect(Collectors.joining("\n"));
    }

}