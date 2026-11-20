package com.backend.sigcapi.enums;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OperatorTest {

    @ParameterizedTest(name = "{0}: apply({1}, {2}) = {3}")
    @CsvSource({
        "EQUALS,       true,    TRUE,   true",
        "EQUALS,       abc,     abd,    false",
        "NOT_EQUALS,   abc,     abd,    true",
        "NOT_EQUALS,   abc,     ABC,    false",
        "CONTAINS,     Hola Mundo, mundo, true",
        "CONTAINS,     Hola,    adios,  false",
        "GREATER_THAN, 10,      5,      true",
        "GREATER_THAN, 5,       5,      false",
        "LESS_THAN,    3,       5,      true",
        "LESS_THAN,    7,       5,      false",
        "GREATER_THAN, abc,     5,      false",
        "LESS_THAN,    5,       xyz,    false",
        "IS_NOT_NULL,  algo,    ignorado, true"
    })
    @DisplayName("Cada operador evalúa correctamente valores reales")
    void evaluaOperadores(Operator op, String actual, String expected, boolean esperado) {
        assertEquals(esperado, op.apply(actual, expected));
    }

    @Test
    @DisplayName("Valor nulo: EQUALS y CONTAINS fallan, NOT_EQUALS se cumple, IS_NOT_NULL falla")
    void valoresNulos() {
        assertFalse(Operator.EQUALS.apply(null, "x"));
        assertTrue(Operator.NOT_EQUALS.apply(null, "x"));
        assertFalse(Operator.CONTAINS.apply(null, "x"));
        assertFalse(Operator.GREATER_THAN.apply(null, "1"));
        assertFalse(Operator.LESS_THAN.apply(null, "1"));
        assertFalse(Operator.IS_NOT_NULL.apply(null, null));
    }
}
