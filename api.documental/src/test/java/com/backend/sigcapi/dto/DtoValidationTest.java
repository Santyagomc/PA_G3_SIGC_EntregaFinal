package com.backend.sigcapi.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.backend.sigcapi.enums.RequesType;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class DtoValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    private static <T> Set<String> campos(T dto) {
        return validator.validate(dto).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    private static <T> String mensaje(T dto, String campo) {
        return validator.validate(dto).stream()
                .filter(v -> v.getPropertyPath().toString().equals(campo))
                .map(ConstraintViolation::getMessage).findFirst().orElse(null);
    }

    // ----- RegisterDTO -----

    @Test
    @DisplayName("RegisterDTO válido no produce violaciones")
    void registroValido() {
        assertTrue(campos(new RegisterDTO("Maria Fernanda Lopez", "maria@example.com", "12345678", "Clave123")).isEmpty());
    }

    @Test
    @DisplayName("RegisterDTO: nombre corto, correo inválido, documento corto y clave débil fallan a la vez")
    void registroInvalido() {
        RegisterDTO dto = new RegisterDTO("Ana", "correo-malo", "123", "minuscula");
        assertEquals(Set.of("name", "email", "document", "password"), campos(dto));
        assertEquals("Correo inválido", mensaje(dto, "email"));
    }

    @Test
    @DisplayName("RegisterDTO: los campos nulos se reportan como obligatorios")
    void registroNulos() {
        RegisterDTO dto = new RegisterDTO(null, null, null, null);
        assertEquals(Set.of("name", "email", "document", "password"), campos(dto));
        assertEquals("El campo name es obligatorio", mensaje(dto, "name"));
    }

    @Test
    @DisplayName("RegisterDTO: la contraseña exige mayúscula, minúscula y dígito")
    void registroPassword() {
        assertFalse(campos(new RegisterDTO("Maria Fernanda Lopez", "m@e.com", "12345678", "sinmayus1")).isEmpty());
        assertFalse(campos(new RegisterDTO("Maria Fernanda Lopez", "m@e.com", "12345678", "SINMINUS1")).isEmpty());
        assertFalse(campos(new RegisterDTO("Maria Fernanda Lopez", "m@e.com", "12345678", "SinDigito")).isEmpty());
        assertTrue(campos(new RegisterDTO("Maria Fernanda Lopez", "m@e.com", "12345678", "Cumple123")).isEmpty());
    }

    // ----- RequestDTO -----

    @Test
    @DisplayName("RequestDTO válido no produce violaciones")
    void solicitudValida() {
        RequestDTO dto = new RequestDTO("Titulo", "Descripcion", "Nombre Completo", "a@b.com", "12345678",
                "Neiva", "3001234567", RequesType.QUEJA, true, false, LocalDate.now().plusDays(10));
        assertTrue(campos(dto).isEmpty());
    }

    @Test
    @DisplayName("RequestDTO: solo con título faltan los demás campos obligatorios")
    void solicitudIncompleta() {
        RequestDTO dto = new RequestDTO("Titulo", null, null, null, null, null, null, null, null, null, null);
        Set<String> c = campos(dto);
        assertTrue(c.containsAll(Set.of("description", "fullName", "email", "document", "city", "phone",
                "type", "acceptedDataPolicy", "expirationDate")));
        assertFalse(c.contains("title"));
    }

    @Test
    @DisplayName("RequestDTO: título de más de 100 caracteres y correo inválido")
    void solicitudLimites() {
        RequestDTO dto = new RequestDTO("x".repeat(101), "Descripcion", "Nombre Completo", "no-es-correo", "12345678",
                "Neiva", "3001234567", RequesType.QUEJA, true, false, LocalDate.now().plusDays(10));
        assertEquals(Set.of("title", "email"), campos(dto));
    }

    // ----- ChangePasswordDTO -----

    @Test
    @DisplayName("ChangePasswordDTO: la nueva clave débil y los campos vacíos se rechazan")
    void cambioDeClave() {
        assertTrue(campos(new ChangePasswordDTO("Actual123", "Nueva123", "Nueva123")).isEmpty());
        Set<String> c = campos(new ChangePasswordDTO("", "debil", ""));
        assertTrue(c.containsAll(Set.of("currentPassword", "newPassword", "confirmPassword")));
    }
}
