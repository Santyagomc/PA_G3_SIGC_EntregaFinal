package com.backend.sigcapi.mapper;

import com.backend.sigcapi.dto.ChatDTO;
import com.backend.sigcapi.dto.SesionDTO;
import com.backend.sigcapi.dto.SesionListDTO;
import com.backend.sigcapi.entities.Chat;
import com.backend.sigcapi.entities.Session;

import java.util.List;

public class SesionMapper {

    public static SesionDTO toDTO(Session sesion) {

        List<ChatDTO> chats = sesion.getChats().stream().map(SesionMapper::toChatDTO).toList();

        return new SesionDTO(sesion.getId(), sesion.getFullName(), sesion.getCreatedAt(), chats);
    }

    public static ChatDTO toChatDTO(Chat chat) {
        return new ChatDTO(chat.getMensaje(), chat.getType(), chat.getCreatedAt());
    }

    public static SesionListDTO toListDTO(Session sesion) {
        return new SesionListDTO(
                sesion.getId(),
                sesion.getFullName(),
                sesion.getCreatedAt(),
                sesion.getClosureAt());
    }
}
