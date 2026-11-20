package com.backend.sigcapi.services.implement;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.backend.sigcapi.dto.RequestDTO;
import com.backend.sigcapi.entities.Rule;
import com.backend.sigcapi.exceptions.BusinessRuleException;
import com.backend.sigcapi.services.IExpertEngineService;

@Service
public class ExpertEngineServiceImpl implements IExpertEngineService {

    @Override
    public List<String> executeRulesBroken(RequestDTO request, int documentSize, List<Rule> rules) {
        List<String> rulesBroken = rules.stream()
                .filter(rule -> {
                    Object valueToEvaluate = getContextValue(request, documentSize, rule.getStandard().getCode());
                    // Si el operador devuelve false, la regla se ha roto
                    return valueToEvaluate != null && !rule.getOperator().apply(valueToEvaluate, rule.getValue());
                })
                .filter(Rule::getIsBlocking)
                .map(Rule::getSuggestion)
                .collect(Collectors.toList());

        if (!rulesBroken.isEmpty()) {
            throw new BusinessRuleException("No se puede radicar debido al incumplimiento de reglas: " + String.join(", ", rulesBroken));
        }

        return rulesBroken;
    }

    /**
     * Calcula los días hábiles restantes basándose en la normativa colombiana.
     */
    private long calculateDays(LocalDate expirationDate) {
        if (expirationDate == null) return 0;

        long businessDays = 0;
        LocalDate current = LocalDate.now();

        while (current.isBefore(expirationDate)) {
            current = current.plusDays(1);
            DayOfWeek dow = current.getDayOfWeek();
            // Excluye fines de semana
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                businessDays++;
            }
        }
        return businessDays;
    }

    /**
     * Mapea la nomenclatura de la regla con el valor real del contexto de la solicitud.
     */
    private Object getContextValue(RequestDTO request, int documentSize, String nomenclature) {
        return switch (nomenclature) {
            case "L-1437" -> calculateDays(request.expirationDate()); // Ley 1437 de 2011
            case "L-1581" -> request.acceptedDataPolicy();         // Ley 1581 de 2012
            case "AUD-COR" -> request.mailStatus();
            case "DOC-CANT" -> documentSize;
            default -> null;
        };
    }
}