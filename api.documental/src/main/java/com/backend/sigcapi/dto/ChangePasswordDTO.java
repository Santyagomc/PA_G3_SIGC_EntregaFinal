package com.backend.sigcapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordDTO(
    @NotBlank(message = "La contraseña actual es requerida")
    String currentPassword,
    
    @NotBlank(message = "La nueva contraseña es requerida")
	@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$", message = "La contraseña debe contener al menos una mayúscula, una minúscula y un dígito")
    String newPassword,
    
    @NotBlank(message = "La confirmación de contraseña es requerida")
    String confirmPassword
) {}
