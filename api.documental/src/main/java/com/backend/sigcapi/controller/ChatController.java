package com.backend.sigcapi.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.backend.sigcapi.dto.ChatDTO;
import com.backend.sigcapi.dto.ChatSaveDTO;
import com.backend.sigcapi.services.IChatService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/chats")
public class ChatController {

    private final IChatService service;

    public ChatController(IChatService service) {
        this.service = service;
    }

    @PostMapping("/save")
    @ResponseStatus(HttpStatus.CREATED)
	public ResponseEntity<ChatDTO> save(@Valid @RequestBody ChatSaveDTO chat) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.save(chat));
	}
    
}
