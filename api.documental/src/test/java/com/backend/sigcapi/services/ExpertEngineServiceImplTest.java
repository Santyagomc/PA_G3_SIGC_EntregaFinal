package com.backend.sigcapi.services;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.backend.sigcapi.dto.RequestDTO;
import com.backend.sigcapi.entities.Rule;
import com.backend.sigcapi.entities.Standard;
import com.backend.sigcapi.enums.Operator;
import com.backend.sigcapi.enums.RequesType;
import com.backend.sigcapi.exceptions.BusinessRuleException;
import com.backend.sigcapi.services.implement.ExpertEngineServiceImpl;

class ExpertEngineServiceImplTest {

    private final ExpertEngineServiceImpl engine = new ExpertEngineServiceImpl();

    private Rule regla(String code, Operator op, String value, boolean blocking, String suggestion) {
        Standard s = new Standard();
        s.setCode(code);
        Rule r = new Rule();
        r.setStandard(s);
        r.setOperator(op);
        r.setValue(value);
        r.setIsBlocking(blocking);
        r.setSuggestion(suggestion);
        return r;
    }

    private RequestDTO solicitud(boolean aceptaPolitica, LocalDate vence) {
        return new RequestDTO("Titulo", "Descripcion", "Nombre Completo", "a@b.com", "12345678",
                "Neiva", "3001234567", RequesType.QUEJA, aceptaPolitica, false, vence);
    }

    @Test
    @DisplayName("Sin reglas no hay incumplimientos")
    void sinReglas() {
        assertTrue(engine.executeRulesBroken(solicitud(true, LocalDate.now().plusDays(30)), 1, List.of()).isEmpty());
    }

    @Test
    @DisplayName("Regla bloqueante incumplida (L-1581, política de datos) → BusinessRuleException con la sugerencia")
    void reglaBloqueanteIncumplida() {
        Rule r = regla("L-1581", Operator.EQUALS, "true", true, "Debe aceptar la política de datos");
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> engine.executeRulesBroken(solicitud(false, LocalDate.now().plusDays(30)), 1, List.of(r)));
        assertTrue(ex.getMessage().contains("Debe aceptar la política de datos"));
    }

    @Test
    @DisplayName("Regla bloqueante cumplida → no lanza excepción")
    void reglaBloqueanteCumplida() {
        Rule r = regla("L-1581", Operator.EQUALS, "true", true, "sugerencia");
        assertTrue(engine.executeRulesBroken(solicitud(true, LocalDate.now().plusDays(30)), 1, List.of(r)).isEmpty());
    }

    @Test
    @DisplayName("Regla NO bloqueante incumplida → se ignora, no impide radicar")
    void reglaNoBloqueanteIncumplida() {
        Rule r = regla("L-1581", Operator.EQUALS, "true", false, "solo advertencia");
        assertDoesNotThrow(() -> engine.executeRulesBroken(solicitud(false, LocalDate.now().plusDays(30)), 1, List.of(r)));
    }

    @Test
    @DisplayName("DOC-CANT: exige al menos un documento adjunto")
    void cantidadDeDocumentos() {
        Rule r = regla("DOC-CANT", Operator.GREATER_THAN, "0", true, "Adjunte al menos un documento");
        RequestDTO dto = solicitud(true, LocalDate.now().plusDays(30));

        assertThrows(BusinessRuleException.class, () -> engine.executeRulesBroken(dto, 0, List.of(r)));
        assertDoesNotThrow(() -> engine.executeRulesBroken(dto, 2, List.of(r)));
    }

    @Test
    @DisplayName("L-1437: usa los días hábiles hasta la fecha de vencimiento")
    void diasHabiles() {
        Rule r = regla("L-1437", Operator.GREATER_THAN, "10", true, "Plazo insuficiente");

        assertDoesNotThrow(() -> engine.executeRulesBroken(solicitud(true, LocalDate.now().plusDays(60)), 1, List.of(r)));
        assertThrows(BusinessRuleException.class,
                () -> engine.executeRulesBroken(solicitud(true, LocalDate.now().plusDays(3)), 1, List.of(r)));
    }

    @Test
    @DisplayName("Un código de norma desconocido se ignora")
    void codigoDesconocido() {
        Rule r = regla("XXX", Operator.EQUALS, "true", true, "no aplica");
        assertDoesNotThrow(() -> engine.executeRulesBroken(solicitud(false, LocalDate.now().plusDays(30)), 1, List.of(r)));
    }

    @Test
    @DisplayName("Varias reglas bloqueantes incumplidas se reportan en un solo mensaje")
    void variasReglas() {
        Rule a = regla("L-1581", Operator.EQUALS, "true", true, "SUG-A");
        Rule b = regla("DOC-CANT", Operator.GREATER_THAN, "0", true, "SUG-B");
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> engine.executeRulesBroken(solicitud(false, LocalDate.now().plusDays(30)), 0, List.of(a, b)));
        assertTrue(ex.getMessage().contains("SUG-A") && ex.getMessage().contains("SUG-B"));
    }
}
