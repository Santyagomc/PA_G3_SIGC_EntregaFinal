package com.backend.sigcapi.services;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.backend.sigcapi.dto.RequestDTO;
import com.backend.sigcapi.entities.Request;
import com.backend.sigcapi.enums.RequesType;
import com.backend.sigcapi.enums.RequestStatus;

public interface IRequestService {
    Page<Request> all(String search, RequestStatus status, RequesType type, LocalDate from, LocalDate to, Pageable pageable);
    Request findById(Long id);
    Request save(RequestDTO requestDto);
    Request updateStatus(Long id, RequestStatus newStatus, String observations);
    void delete(Long id);
}