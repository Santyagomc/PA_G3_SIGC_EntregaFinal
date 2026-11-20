package com.backend.sigcapi.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.backend.sigcapi.dto.RequestDTO;
import com.backend.sigcapi.dto.RuleDTO;
import com.backend.sigcapi.entities.Rule;
import com.backend.sigcapi.entities.Standard;
import com.backend.sigcapi.enums.Operator;
import com.backend.sigcapi.enums.RequesType;
import com.backend.sigcapi.repositories.RuleRepository;
import com.backend.sigcapi.repositories.StandardRepository;
import com.backend.sigcapi.services.implement.RuleServiceImpl;

@ExtendWith(MockitoExtension.class)
class RuleServiceImplTest {

    @Mock private RuleRepository repository;
    @Mock private StandardRepository standardRepository;
    @Mock private IExpertEngineService engine;
    private RuleServiceImpl service() {
        return new RuleServiceImpl(repository, standardRepository, engine);
    }

    @Test
    @DisplayName("save: construye la regla con todos los campos del DTO, incluido isBlocking")
    void saveMapeaCampos() {
        Standard std = new Standard();
        std.setId(1L);
        when(standardRepository.findById(1L)).thenReturn(Optional.of(std));
        when(repository.save(any(Rule.class))).thenAnswer(inv -> inv.getArgument(0));
        RuleDTO dto = new RuleDTO("Política de datos", 1L, Operator.EQUALS, "true", "Acepte la política", true);

        Rule r = service().save(dto);

        assertEquals("Política de datos", r.getFullName());
        assertEquals(Operator.EQUALS, r.getOperator());
        assertEquals("true", r.getValue());
        assertEquals("Acepte la política", r.getSuggestion());
        assertSame(std, r.getStandard());
        assertEquals(Boolean.TRUE, r.getIsBlocking());
    }

    @Test
    @DisplayName("save: falla si el estándar no existe y no persiste la regla")
    void saveEstandarInexistente() {
        when(standardRepository.findById(9L)).thenReturn(Optional.empty());
        RuleDTO dto = new RuleDTO("Regla", 9L, Operator.EQUALS, "x", "s", false);

        assertThrows(RuntimeException.class, () -> service().save(dto));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("consultLegalDiagnosis: pasa al motor la solicitud, el número de documentos y las reglas")
    void diagnosticoDelegaAlMotor() {
        List<Rule> reglas = List.of(new Rule());
        RequestDTO dto = new RequestDTO("T", "D", "Nombre Completo", "a@b.com", "12345678", "Neiva",
                "3001234567", RequesType.RECLAMO, true, false, LocalDate.now().plusDays(10));
        when(repository.findAll()).thenReturn(reglas);
        when(engine.executeRulesBroken(dto, 2, reglas)).thenReturn(List.of());

        assertTrue(service().consultLegalDiagnosis(dto, 2).isEmpty());
        verify(engine).executeRulesBroken(dto, 2, reglas);
    }

    @Test
    @DisplayName("all: devuelve todas las reglas del repositorio")
    void all() {
        when(repository.findAll()).thenReturn(List.of(new Rule(), new Rule()));
        assertEquals(2, service().all().size());
    }
}
