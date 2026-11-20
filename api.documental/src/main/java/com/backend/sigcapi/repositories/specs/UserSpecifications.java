package com.backend.sigcapi.repositories.specs;

import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;

import com.backend.sigcapi.entities.User;
import com.backend.sigcapi.enums.UserStatus;

public class UserSpecifications {

    public static Specification<User> hasStatus(UserStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<User> hasRole(String role) {
        return (root, query, cb) -> role == null ? null : cb.equal(root.get("rol"), role);
    }

    public static Specification<User> createdBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            if (from == null && to == null)
                return null;

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