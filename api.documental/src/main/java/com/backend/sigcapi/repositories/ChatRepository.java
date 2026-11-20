package com.backend.sigcapi.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.backend.sigcapi.entities.Chat;

public interface ChatRepository extends JpaRepository<Chat, Long> {
    
}
