package com.backend.sigcapi.dto;

import java.time.LocalDate;

import com.backend.sigcapi.enums.RequesType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequestDTO(

    @NotBlank
    @Size(max = 100)
    @NotNull(message = "El campo title es obligatorio")
    String title,

    @NotBlank
    @NotNull(message = "El campo description es obligatorio")
    String description,

    @NotBlank
    @Size(max = 100)
    @NotNull(message = "El campo fullName es obligatorio")
    String fullName,

    @NotBlank
    @Email(message = "Correo invalido")
    @Size(max = 150)
    @NotNull(message = "El campo email es obligatorio")
    String email,

    @NotBlank
    @Size(max = 20)
    @NotNull(message = "El campo document es obligatorio")
    String document,

    @NotBlank
    @Size(max = 80)
    @NotNull(message = "El campo city es obligatorio")
    String city,

    @NotBlank
    @Size(max = 20)
    @NotNull(message = "El campo phone es obligatorio")
    String phone,

    @NotNull(message = "El campo type es obligatorio")
    RequesType type,

    @NotNull(message = "El campo acceptedDataPolicy es obligatorio")
    Boolean acceptedDataPolicy,

    Boolean mailStatus,

    @NotNull(message = "El campo expirationDate es obligatorio")
    LocalDate expirationDate

) {}
