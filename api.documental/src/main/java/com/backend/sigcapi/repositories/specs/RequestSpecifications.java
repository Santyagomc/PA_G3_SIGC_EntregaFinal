package com.backend.sigcapi.repositories.specs;

import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;

import com.backend.sigcapi.entities.Request;
import com.backend.sigcapi.enums.RequesType;
import com.backend.sigcapi.enums.RequestStatus;

public class RequestSpecifications {

    public static Specification<Request> hasSearchText(String text) {
        return (root, query, cb) -> {
            if (text == null || text.isEmpty()) return null;
            String wildcard = "%" + text.toLowerCase() + "%";
            return cb.or(
                cb.like(cb.lower(root.get("title")), wildcard),
                cb.like(cb.lower(root.get("document")), wildcard)
            );
        };
    }

    public static Specification<Request> hasStatus(RequestStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Request> hasType(RequesType type) {
        return (root, query, cb) -> type == null ? null : cb.equal(root.get("type"), type);
    }

    public static Specification<Request> createdBetween(LocalDate from, LocalDate to) {
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