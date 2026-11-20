package com.backend.sigcapi.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.backend.sigcapi.entities.Standard;

public interface StandardRepository  extends JpaRepository<Standard, Long> {
    
}
