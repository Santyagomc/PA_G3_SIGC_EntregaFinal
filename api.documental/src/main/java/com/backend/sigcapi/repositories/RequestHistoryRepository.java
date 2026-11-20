package com.backend.sigcapi.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.backend.sigcapi.entities.RequestHistory;

/*
 * Entrega 2 (P2): se elimina el método findByRequestId(Specification, Pageable) de la entrega
 * anterior —Spring Data lo interpretaba como consulta derivada y no aplicaba la Specification—;
 * el servicio usa ahora findAll(Specification, Pageable) de JpaSpecificationExecutor.
 */
public interface RequestHistoryRepository extends JpaRepository<RequestHistory, Long>, JpaSpecificationExecutor<RequestHistory> {

    long countByRequestId(Long requestId);

    boolean existsByEventId(String eventId);

    void deleteByRequestId(Long requestId);
}
