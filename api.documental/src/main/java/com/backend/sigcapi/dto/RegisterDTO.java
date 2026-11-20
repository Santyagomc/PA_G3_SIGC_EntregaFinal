package com.backend.sigcapi.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos del registro público de ciudadanos. No incluye rol ni estado: toda cuenta
 * creada por este medio es de tipo CONSULTOR y queda ACTIVA. Si el cliente envía
 * campos adicionales (por ejemplo "admin"), se ignoran.
 */
public record RegisterDTO(
	@NotNull(message = "El campo name es obligatorio")
	@Size(min = 10, max = 100, message = "El nombre completo debe tener al menos 10 caracteres y maximo 100.")
	String name,
	@NotNull(message = "El campo email es obligatorio")
	@Email(message = "Correo inválido")
	String email,
	@NotNull(message = "El campo document es obligatorio")
	@Size(min = 8, message = "El documento debe tener al menos 8 caracteres.")
	String document,
	@NotNull(message = "El campo password es obligatorio")
	@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$", message = "La contraseña debe contener al menos una mayúscula, una minúscula y un dígito")
	String password) {
}
