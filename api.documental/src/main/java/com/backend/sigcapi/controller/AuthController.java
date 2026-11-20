package com.backend.sigcapi.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backend.sigcapi.dto.LoginDTO;
import com.backend.sigcapi.dto.RegisterDTO;
import com.backend.sigcapi.dto.UserDTO;
import com.backend.sigcapi.enums.UserStatus;
import com.backend.sigcapi.entities.User;
import com.backend.sigcapi.repositories.UserRepository;
import com.backend.sigcapi.security.JwtService;
import com.backend.sigcapi.services.IUserService;
import com.backend.sigcapi.util.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final IUserService service;
    private final UserRepository repository;
    private final AuthenticationManager authManager;
    private final JwtService jwtService;

    public AuthController(IUserService service, UserRepository repository, AuthenticationManager authManager, JwtService jwtService) {
        this.service = service;
        this.repository = repository;
        this.authManager = authManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginDTO dto) {

        var optionalUser = repository.findByDocument(dto.username());

        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(false, "Credenciales inválidas."));
        }

        User usuario = optionalUser.get();

        if (service.isUserLocked(usuario)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(false,
                            "Usuario bloqueado temporalmente por múltiples intentos fallidos. Inténtalo más tarde."));
        }

        try {

            Authentication auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.username(), dto.password()));

            SecurityContextHolder.getContext().setAuthentication(auth);

            // Resetear intentos fallidos
            service.resetFailedAttempts(usuario);

            //Generar Token
            String token = jwtService.generateToken(usuario.getDocument(), usuario.getRol());

            return ResponseEntity.ok(Map.of(
                    "exito", true,
                    "mensaje", "Inicio de sesión exitoso",
                    "token", token,
                    "usuario", usuario.getDocument(),
                    "roles", List.of(usuario.getRol())));

        } catch (org.springframework.security.core.AuthenticationException e) {

            // Incrementar intentos fallidos
            service.increaseFailedAttempts(usuario);

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(false, "Credenciales inválidas."));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterDTO dto) {
        try {
            // El registro público siempre crea un usuario CONSULTOR (admin = false).
            service.save(new UserDTO(dto.name(), dto.email(), dto.document(), dto.password(),
                    false, UserStatus.ACTIVO));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponse<>(true, "Usuario registrado exitosamente."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, e.getMessage()));
        }
    }

}
