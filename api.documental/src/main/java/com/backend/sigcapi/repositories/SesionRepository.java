package com.backend.sigcapi.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.backend.sigcapi.entities.Session;

public interface SesionRepository extends JpaRepository<Session, Long>, JpaSpecificationExecutor<Session> {
    
}
