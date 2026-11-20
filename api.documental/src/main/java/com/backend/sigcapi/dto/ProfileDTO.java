package com.backend.sigcapi.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProfileDTO(
	@NotNull(message = "El campo name es obligatorio")
	@Size(min = 10, max = 100, message = "El nombre completo debe tener al menos 10 caracteres y maximo 100.") 
	String name,
	@NotNull(message = "El campo email es obligatorio")
	@Email(message = "Correo inválido")
	String email){
}
