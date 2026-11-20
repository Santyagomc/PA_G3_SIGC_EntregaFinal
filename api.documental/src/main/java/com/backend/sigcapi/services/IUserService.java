package com.backend.sigcapi.services;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.backend.sigcapi.dto.ProfileDTO;
import com.backend.sigcapi.dto.UserDTO;
import com.backend.sigcapi.entities.User;
import com.backend.sigcapi.enums.UserStatus;

public interface IUserService {

    Page<User> all(UserStatus status, String rol, LocalDate from, LocalDate to, Pageable pageable);

    User findById(Long id);

    User getAuthenticatedUser();

    User save(UserDTO userDto);

    User updateProfile(Long userId, ProfileDTO profileDto);

    User update(Long idUser, UserDTO userDto);

    void changePassword(Long userId, String currentPassword, String newPassword);

    void delete(Long id);

    void increaseFailedAttempts(User user);

    void resetFailedAttempts(User user);

    boolean isUserLocked(User user);
}