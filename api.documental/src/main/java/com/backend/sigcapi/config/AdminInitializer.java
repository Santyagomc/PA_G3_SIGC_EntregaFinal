package com.backend.sigcapi.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.backend.sigcapi.dto.UserDTO;
import com.backend.sigcapi.enums.UserStatus;
import com.backend.sigcapi.repositories.UserRepository;
import com.backend.sigcapi.services.IUserService;

/**
 * Crea el administrador inicial al arrancar, a partir de variables de entorno
 * (ADMIN_DOCUMENT, ADMIN_PASSWORD, ADMIN_EMAIL, ADMIN_NAME). El registro público
 * nunca crea administradores; los demás los crea un ADMIN con POST /admin/users/save.
 * Si no se configuran las variables, no se crea ningún administrador.
 */
@Component
public class AdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);
    private static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$";

    private final IUserService userService;
    private final UserRepository userRepository;

    @Value("${com.sigcapi.bootstrap.admin.document:}")
    private String document;

    @Value("${com.sigcapi.bootstrap.admin.password:}")
    private String password;

    @Value("${com.sigcapi.bootstrap.admin.email:}")
    private String email;

    @Value("${com.sigcapi.bootstrap.admin.name:Administrador SIGC}")
    private String name;

    public AdminInitializer(IUserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        if (document.isBlank() || password.isBlank() || email.isBlank()) {
            log.info("Administrador inicial no configurado (ADMIN_DOCUMENT/ADMIN_PASSWORD/ADMIN_EMAIL); se omite.");
            return;
        }
        if (!password.matches(PASSWORD_PATTERN)) {
            log.error("ADMIN_PASSWORD no cumple la política (mínimo 8 caracteres con mayúscula, minúscula y dígito); no se crea el administrador.");
            return;
        }
        if (userRepository.findByDocument(document).isPresent()) {
            log.info("El administrador inicial ya existe; no se modifica.");
            return;
        }
        userService.save(new UserDTO(name, email, document, password, true, UserStatus.ACTIVO));
        log.info("Administrador inicial creado.");
    }
}
