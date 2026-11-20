package com.backend.sigcapi.enums;

import java.util.Objects;

public enum Operator {
    EQUALS {
        @Override
        public boolean apply(Object actual, String expected) {
            if (actual == null) return false;
            return actual.toString().equalsIgnoreCase(expected);
        }
    },
    NOT_EQUALS {
        @Override
        public boolean apply(Object actual, String expected) {
            if (actual == null) return true; // Si es nulo, es diferente a lo esperado
            return !actual.toString().equalsIgnoreCase(expected);
        }
    },
    CONTAINS {
        @Override
        public boolean apply(Object actual, String expected) {
            if (actual == null || expected == null) return false;
            return actual.toString().toLowerCase().contains(expected.toLowerCase());
        }
    },
    GREATER_THAN {
        @Override
        public boolean apply(Object actual, String expected) {
            try {
                return actual != null && Double.parseDouble(actual.toString()) > Double.parseDouble(expected);
            } catch (NumberFormatException e) {
                return false;
            }
        }
    },
    LESS_THAN {
        @Override
        public boolean apply(Object actual, String expected) {
            try {
                return actual != null && Double.parseDouble(actual.toString()) < Double.parseDouble(expected);
            } catch (NumberFormatException e) {
                return false;
            }
        }
    },
    IS_NOT_NULL {
        @Override
        public boolean apply(Object actual, String expected) {
            return Objects.nonNull(actual);
        }
    };

    /**
     * Firma del método abstracto. 
     * Cada constante del Enum está obligada a implementarlo.
     */
    public abstract boolean apply(Object actual, String expected);
}