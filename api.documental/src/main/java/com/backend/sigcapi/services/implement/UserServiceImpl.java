package com.backend.sigcapi.services.implement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.sigcapi.dto.ProfileDTO;
import com.backend.sigcapi.dto.UserDTO;
import com.backend.sigcapi.entities.User;
import com.backend.sigcapi.enums.UserStatus;
import com.backend.sigcapi.exceptions.InvalidPasswordException;
import com.backend.sigcapi.exceptions.ResourceNotFoundException;
import com.backend.sigcapi.repositories.UserRepository;
import com.backend.sigcapi.repositories.specs.UserSpecifications;
import com.backend.sigcapi.services.IUserService;

@Service
public class UserServiceImpl implements IUserService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository repository;

    @Value("${com.sigcapi.security.max-failed-attempts}")
    private int maxFailedAttempts;

    @Value("${com.sigcapi.security.lock-duration-minutes}")
    private int lockDurationMinutes;

    public UserServiceImpl(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        this.repository = repository;
    }

    @Override
    public Page<User> all(UserStatus status, String rol, LocalDate from, LocalDate to, Pageable pageable) {
        Specification<User> spec = Specification
                .where(UserSpecifications.createdBetween(from, to))
                .and(UserSpecifications.hasRole(rol))
                .and(UserSpecifications.hasStatus(status));

        return repository.findAll(spec, pageable);
    }

    @Override
    public User findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el usuario con ID: " + id));
    }

    @Override
    @Transactional
    public User save(UserDTO userDto) {
        validateDocument(userDto.document(), null);
        User user = new User();
        mapUserData(user, userDto);
        user.setStatus(UserStatus.ACTIVO);
        user.setPassword(passwordEncoder.encode(userDto.password()));
        return repository.save(user);
    }

    @Override
    @Transactional
    public User update(Long idUser, UserDTO userDto) {
        User user = repository.findById(idUser)
                .orElseThrow(() -> new ResourceNotFoundException("El usuario no existe"));

        validateDocument(userDto.document(), idUser);
        mapUserData(user, userDto);

        if (userDto.password() != null && !userDto.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(userDto.password()));
        }
        return repository.save(user);
    }

    @Override
    @Transactional
    public User updateProfile(Long userId, ProfileDTO profileDto) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        user.setFullName(profileDto.name());
        user.setEmail(profileDto.email());

        return repository.save(user);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = repository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidPasswordException("Contraseña actual incorrecta");
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new InvalidPasswordException("La nueva contraseña debe ser diferente a la actual");
        }

        user.setPassword(passwordEncoder.encode(newPassword));

        resetFailedAttempts(user);

        repository.save(user);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        User user = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("El usuario no existe"));
        repository.delete(user);
    }

    public User getAuthenticatedUser() {
        String document = SecurityContextHolder.getContext().getAuthentication().getName();
        return repository.findByDocument(document)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    @Override
    @Transactional
    public void increaseFailedAttempts(User user) {
        int newFailAttempts = user.getFailedAttempts() + 1;
        user.setFailedAttempts(newFailAttempts);

        if (newFailAttempts >= maxFailedAttempts) {
            LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(lockDurationMinutes);
            user.setLockUntil(lockUntil);
        }
        this.repository.save(user);
    }

    @Override
    @Transactional
    public void resetFailedAttempts(User user) {
        user.setFailedAttempts(0);
        user.setLockUntil(null);
        this.repository.save(user);
    }

    @Override
    public boolean isUserLocked(User user) {
        LocalDateTime lockUntil = user.getLockUntil();
        if (lockUntil == null)
            return false;

        if (LocalDateTime.now().isAfter(lockUntil)) {
            resetFailedAttempts(user);
            return false;
        }
        return true;
    }

    private void validateDocument(String document, Long currentUserId) {
        repository.findByDocument(document)
                .filter(u -> !u.getId().equals(currentUserId))
                .ifPresent(u -> {
                    throw new IllegalArgumentException("Documento ya existe");
                });
    }

    private static void mapUserData(User user, UserDTO dto) {
        user.setFullName(dto.name());
        user.setEmail(dto.email());
        user.setDocument(dto.document());
        user.setRol(dto.admin() ? "ADMIN" : "CONSULTOR");
    }
}