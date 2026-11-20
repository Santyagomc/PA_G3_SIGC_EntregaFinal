package com.backend.sigcapi.services;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.backend.sigcapi.dto.SesionDTO;
import com.backend.sigcapi.dto.SesionListDTO;

public interface ISesionService {
    Page<SesionListDTO> all(LocalDate from, LocalDate to, Pageable pageable);
    SesionDTO findById(Long id);
    SesionDTO save(String fullName);
    void logOut(Long id);
}