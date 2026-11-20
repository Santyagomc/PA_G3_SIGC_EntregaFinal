package com.backend.sigcapi.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.backend.sigcapi.entities.FailedEvent;

public interface FailedEventRepository extends JpaRepository<FailedEvent, Long> {

    long countByExceptionClassContaining(String exceptionClassFragment);
}
