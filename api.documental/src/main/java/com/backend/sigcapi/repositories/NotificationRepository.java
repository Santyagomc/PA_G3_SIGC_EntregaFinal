package com.backend.sigcapi.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.backend.sigcapi.entities.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRequestId(Long requestId, Pageable pageable);

    long countByRequestId(Long requestId);
}
