package com.backend.sigcapi.security;

import java.time.LocalDateTime;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.backend.sigcapi.entities.User;
import com.backend.sigcapi.repositories.UserRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository repository;

    public CustomUserDetailsService(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String document) throws UsernameNotFoundException {

        User usuario = repository.findByDocument(document).orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        boolean accountNonLocked = usuario.getLockUntil() == null || LocalDateTime.now().isAfter(usuario.getLockUntil());

        return org.springframework.security.core.userdetails.User
                .withUsername(usuario.getDocument())
                .password(usuario.getPassword())
                .accountLocked(!accountNonLocked)
                .roles(usuario.getRol().replace("ROLE_", ""))
                .build();
    }
}