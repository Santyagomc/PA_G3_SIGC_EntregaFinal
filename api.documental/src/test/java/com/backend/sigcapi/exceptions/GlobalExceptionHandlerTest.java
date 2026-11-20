package com.backend.sigcapi.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import com.backend.sigcapi.util.ApiResponse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private void verificar(ResponseEntity<? extends ApiResponse<?>> r, HttpStatus esperado) {
        assertEquals(esperado, r.getStatusCode());
        assertFalse(r.getBody().isExito());
        assertNotNull(r.getBody().getMensaje());
    }

    @Test
    @DisplayName("ResourceNotFoundException → 404 con su mensaje")
    void noEncontrado() {
        ResponseEntity<ApiResponse<String>> r = handler.handleResourceNotFound(new ResourceNotFoundException("No existe"));
        verificar(r, HttpStatus.NOT_FOUND);
        assertEquals("No existe", r.getBody().getMensaje());
    }

    @Test
    @DisplayName("BusinessRuleException e InvalidPasswordException → 400")
    void reglasDeNegocio() {
        verificar(handler.handleBusinessRuleException(new BusinessRuleException("regla")), HttpStatus.BAD_REQUEST);
        verificar(handler.handleBusinessRuleException(new InvalidPasswordException("clave")), HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("AuthenticationException → 401")
    void noAutenticado() {
        verificar(handler.handleAuthentication(new BadCredentialsException("x")), HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("AccessDeniedException → 403")
    void sinPermisos() {
        verificar(handler.handleAccessDenied(new AccessDeniedException("x")), HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("DataIntegrityViolationException → 409")
    void conflicto() {
        verificar(handler.handleConflict(new DataIntegrityViolationException("dup")), HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Error inesperado → 500 sin exponer el detalle interno")
    void errorInterno() {
        ResponseEntity<ApiResponse<String>> r = handler.handleGlobalException(new RuntimeException("secreto-interno"));
        verificar(r, HttpStatus.INTERNAL_SERVER_ERROR);
        assertFalse(r.getBody().getMensaje().contains("secreto-interno"));
    }
}
