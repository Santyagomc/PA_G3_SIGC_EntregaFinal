package com.backend.sigcapi.services.implement;

import java.util.List;
import org.springframework.stereotype.Service;

import com.backend.sigcapi.entities.Standard;
import com.backend.sigcapi.repositories.StandardRepository;
import com.backend.sigcapi.services.IStandardService;

@Service
public class StandardServiceImpl implements IStandardService {

    private final StandardRepository repository;

    public StandardServiceImpl(StandardRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Standard> all() {
        return repository.findAll();
    }

    @Override
    public Standard save(Standard standard) {
        // Guarda el estándar (ej. ISO 9001, Ley 1437) que alimentará al motor de reglas
        return repository.save(standard);
    }
}