package com.backend.sigcapi.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.backend.sigcapi.dto.UserDTO;
import com.backend.sigcapi.entities.User;
import com.backend.sigcapi.enums.UserStatus;
import com.backend.sigcapi.exceptions.InvalidPasswordException;
import com.backend.sigcapi.exceptions.ResourceNotFoundException;
import com.backend.sigcapi.repositories.UserRepository;
import com.backend.sigcapi.services.implement.UserServiceImpl;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository repository;
    @Mock private PasswordEncoder passwordEncoder;
    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(repository, passwordEncoder);
        ReflectionTestUtils.setField(service, "maxFailedAttempts", 3);
        ReflectionTestUtils.setField(service, "lockDurationMinutes", 2);
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private UserDTO dto(String document, String password, boolean admin) {
        return new UserDTO("Maria Fernanda Lopez", "maria@example.com", document, password, admin, UserStatus.ACTIVO);
    }

    private User usuario(Long id, String document) {
        User u = new User();
        u.setId(id);
        u.setDocument(document);
        u.setPassword("HASH");
        return u;
    }

    // ---------- save / update ----------

    @Test
    @DisplayName("save: codifica la contraseña, deja el usuario ACTIVO y rol CONSULTOR si admin=false")
    void saveConsultor() {
        when(repository.findByDocument("12345678")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Clave123")).thenReturn("HASH");
        when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = service.save(dto("12345678", "Clave123", false));

        assertEquals("CONSULTOR", saved.getRol());
        assertEquals(UserStatus.ACTIVO, saved.getStatus());
        assertEquals("HASH", saved.getPassword());
        assertNotEquals("Clave123", saved.getPassword());
    }

    @Test
    @DisplayName("save: asigna rol ADMIN cuando admin=true")
    void saveAdmin() {
        when(repository.findByDocument("12345678")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("HASH");
        when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("ADMIN", service.save(dto("12345678", "Clave123", true)).getRol());
    }

    @Test
    @DisplayName("save: rechaza un documento que ya existe y no guarda")
    void saveDocumentoDuplicado() {
        when(repository.findByDocument("12345678")).thenReturn(Optional.of(usuario(5L, "12345678")));

        assertThrows(IllegalArgumentException.class, () -> service.save(dto("12345678", "Clave123", false)));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("update: permite conservar el mismo documento y no recodifica una contraseña vacía")
    void updateMismoDocumentoSinCambiarClave() {
        User existente = usuario(1L, "12345678");
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        when(repository.findByDocument("12345678")).thenReturn(Optional.of(existente));
        when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = service.update(1L, dto("12345678", "", false));

        assertEquals("HASH", result.getPassword());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("update: lanza ResourceNotFoundException si el usuario no existe")
    void updateInexistente() {
        when(repository.findById(9L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.update(9L, dto("12345678", "Clave123", false)));
    }

    @Test
    @DisplayName("findById: lanza ResourceNotFoundException si no existe")
    void findByIdInexistente() {
        when(repository.findById(3L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.findById(3L));
    }

    // ---------- changePassword ----------

    @Test
    @DisplayName("changePassword: clave actual incorrecta → InvalidPasswordException")
    void changePasswordClaveActualIncorrecta() {
        when(repository.findById(1L)).thenReturn(Optional.of(usuario(1L, "12345678")));
        when(passwordEncoder.matches("Mala123", "HASH")).thenReturn(false);

        assertThrows(InvalidPasswordException.class, () -> service.changePassword(1L, "Mala123", "Nueva123"));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("changePassword: la nueva clave no puede ser igual a la actual")
    void changePasswordNuevaIgualActual() {
        when(repository.findById(1L)).thenReturn(Optional.of(usuario(1L, "12345678")));
        when(passwordEncoder.matches("Igual123", "HASH")).thenReturn(true);

        assertThrows(InvalidPasswordException.class, () -> service.changePassword(1L, "Igual123", "Igual123"));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("changePassword: guarda la nueva clave codificada y limpia los intentos fallidos")
    void changePasswordExitoso() {
        User u = usuario(1L, "12345678");
        u.setFailedAttempts(2);
        when(repository.findById(1L)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("Actual123", "HASH")).thenReturn(true);
        when(passwordEncoder.matches("Nueva123", "HASH")).thenReturn(false);
        when(passwordEncoder.encode("Nueva123")).thenReturn("NUEVO_HASH");

        service.changePassword(1L, "Actual123", "Nueva123");

        assertEquals("NUEVO_HASH", u.getPassword());
        assertEquals(0, u.getFailedAttempts());
        verify(repository, atLeastOnce()).save(u);
    }

    // ---------- bloqueo por intentos fallidos ----------

    @Test
    @DisplayName("increaseFailedAttempts: por debajo del máximo suma el intento sin bloquear")
    void intentosFallidosSinBloqueo() {
        User u = usuario(1L, "12345678");
        service.increaseFailedAttempts(u);
        assertEquals(1, u.getFailedAttempts());
        assertNull(u.getLockUntil());
        verify(repository).save(u);
    }

    @Test
    @DisplayName("increaseFailedAttempts: al alcanzar el máximo bloquea la cuenta en el futuro")
    void intentosFallidosBloqueaAlMaximo() {
        User u = usuario(1L, "12345678");
        u.setFailedAttempts(2);
        service.increaseFailedAttempts(u);
        assertEquals(3, u.getFailedAttempts());
        assertNotNull(u.getLockUntil());
        assertTrue(u.getLockUntil().isAfter(LocalDateTime.now()));
    }

    @Test
    @DisplayName("isUserLocked: sin fecha de bloqueo → false")
    void noBloqueadoSinFecha() {
        assertFalse(service.isUserLocked(usuario(1L, "12345678")));
    }

    @Test
    @DisplayName("isUserLocked: bloqueo vigente → true")
    void bloqueoVigente() {
        User u = usuario(1L, "12345678");
        u.setLockUntil(LocalDateTime.now().plusMinutes(5));
        assertTrue(service.isUserLocked(u));
    }

    @Test
    @DisplayName("isUserLocked: bloqueo vencido → false y reinicia contadores")
    void bloqueoVencidoSeReinicia() {
        User u = usuario(1L, "12345678");
        u.setFailedAttempts(5);
        u.setLockUntil(LocalDateTime.now().minusMinutes(1));

        assertFalse(service.isUserLocked(u));
        assertEquals(0, u.getFailedAttempts());
        assertNull(u.getLockUntil());
    }

    // ---------- usuario autenticado ----------

    @Test
    @DisplayName("getAuthenticatedUser: busca por el documento del contexto de seguridad")
    void usuarioAutenticado() {
        User u = usuario(1L, "12345678");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("12345678", null));
        when(repository.findByDocument("12345678")).thenReturn(Optional.of(u));

        assertSame(u, service.getAuthenticatedUser());
    }
}
