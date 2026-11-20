package com.backend.sigcapi.services.implement;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.sigcapi.entities.RequestHistory;
import com.backend.sigcapi.repositories.RequestHistoryRepository;
import com.backend.sigcapi.repositories.specs.RequestHistorySpecifications;
import com.backend.sigcapi.services.IRequestHistoryService;

@Service
public class RequestHistoryServiceImpl implements IRequestHistoryService {

    private final RequestHistoryRepository repository;

    public RequestHistoryServiceImpl(RequestHistoryRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RequestHistory> getHistoryByRequestId(Long idRequest, LocalDate from, LocalDate to, Pageable pageable) {

        Specification<RequestHistory> spec = Specification
                .where(RequestHistorySpecifications.hasRequest(idRequest))
                .and(RequestHistorySpecifications.createdBetween(from, to));

        return repository.findAll(spec, pageable);
    }

    @Override
    @Transactional
    public void deleteByRequestId(Long idRequest) {
        repository.deleteByRequestId(idRequest);
    }

}
