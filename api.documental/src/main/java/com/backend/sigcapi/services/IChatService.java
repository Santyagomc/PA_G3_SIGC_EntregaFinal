package com.backend.sigcapi.services;

import com.backend.sigcapi.dto.ChatDTO;
import com.backend.sigcapi.dto.ChatSaveDTO;

public interface IChatService {
    ChatDTO save(ChatSaveDTO chatDTO);
    String generateResponse(String userMessage, Long sessionId);
}