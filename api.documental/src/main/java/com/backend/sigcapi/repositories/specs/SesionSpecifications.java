package com.backend.sigcapi.repositories.specs;

import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;

import com.backend.sigcapi.entities.Session;

public class SesionSpecifications {

    public static Specification<Session> createdBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            if (from == null && to == null) return null;

            if (from != null && to != null) {
                // Filtra desde el inicio del día 'from' hasta el final del día 'to'
                return cb.between(root.get("createdAt"), from.atStartOfDay(), to.atTime(23, 59, 59));
            } else if (from != null) {
                return cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay());
            } else {
                return cb.lessThanOrEqualTo(root.get("createdAt"), to.atTime(23, 59, 59));
            }
        };
    }
}