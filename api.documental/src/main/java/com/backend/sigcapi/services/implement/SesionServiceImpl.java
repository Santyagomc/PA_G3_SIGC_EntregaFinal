package com.backend.sigcapi.services.implement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.backend.sigcapi.dto.SesionDTO;
import com.backend.sigcapi.dto.SesionListDTO;
import com.backend.sigcapi.entities.Session;
import com.backend.sigcapi.mapper.SesionMapper;
import com.backend.sigcapi.repositories.SesionRepository;
import com.backend.sigcapi.repositories.specs.SesionSpecifications;
import com.backend.sigcapi.services.ISesionService;

@Service
public class SesionServiceImpl implements ISesionService {

    private final SesionRepository repository;

    public SesionServiceImpl(SesionRepository repository) {
        this.repository = repository;
    }

    @Override
    public Page<SesionListDTO> all(LocalDate from, LocalDate to, Pageable pageable) {

        Specification<Session> spec = Specification
                .where(SesionSpecifications.createdBetween(from, to));

        return repository.findAll(spec, pageable)
                .map(SesionMapper::toListDTO);
    }

    @Override
    public SesionDTO findById(Long id) {
        Session sesion = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sesión no encontrada con ID: " + id));

        return SesionMapper.toDTO(sesion);
    }

    @Override
    public SesionDTO save(String fullName) {
        Session sesion = new Session();
        sesion.setFullName(fullName);

        Session sesionSave = repository.save(sesion);
        return SesionMapper.toDTO(sesionSave);
    }

    @Override
    public void logOut(Long id) {
        Session sesion = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sesión no encontrada con ID: " + id));

        sesion.setClosureAt(LocalDateTime.now());
        repository.save(sesion);
    }
}