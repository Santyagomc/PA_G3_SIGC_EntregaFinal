package com.backend.sigcapi.repositories.specs;

import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;

import com.backend.sigcapi.entities.RequestHistory;

public class RequestHistorySpecifications {

    public static Specification<RequestHistory> hasRequest(Long idRequest) {
        return (root, query, cb) -> idRequest == null ? null : cb.equal(root.get("request").get("id"), idRequest);
    }

    
    public static Specification<RequestHistory> createdBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            if (from == null && to == null) return null;

            if (from != null && to != null) {
                // Filtra desde el inicio del día 'from' hasta el final del día 'to'
                return cb.between(root.get("changedAt"), from.atStartOfDay(), to.atTime(23, 59, 59));
            } else if (from != null) {
                return cb.greaterThanOrEqualTo(root.get("changedAt"), from.atStartOfDay());
            } else {
                return cb.lessThanOrEqualTo(root.get("changedAt"), to.atTime(23, 59, 59));
            }
        };
    }
}