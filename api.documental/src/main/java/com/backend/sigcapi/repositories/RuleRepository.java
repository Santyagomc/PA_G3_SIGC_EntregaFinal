package com.backend.sigcapi.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.backend.sigcapi.entities.Rule;

public interface RuleRepository extends JpaRepository<Rule, Long> {

    // Filtra reglas por la nomenclatura del estándar
    List<Rule> findByStandardCodeIn(List<String> codes);
    
}