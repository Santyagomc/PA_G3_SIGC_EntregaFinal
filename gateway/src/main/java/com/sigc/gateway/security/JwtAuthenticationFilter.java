package com.sigc.gateway.security;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autenticación en el gateway: si llega "Authorization: Bearer ...", valida firma y vigencia del token
 * y deja en el contexto la identidad (documento) y los roles. Un token inválido no se acepta: la
 * petición continúa como anónima y las operaciones protegidas responden UNAUTHORIZED.
 * El token nunca se escribe en los logs.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
	private final JwtValidator validator;

	public JwtAuthenticationFilter(JwtValidator validator) {
		this.validator = validator;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith("Bearer ")) {
			String token = header.substring(7).trim();
			try {
				JwtValidator.Identity identity = validator.validate(token);
				List<SimpleGrantedAuthority> authorities = identity.roles().stream()
						.map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
				var auth = new UsernamePasswordAuthenticationToken(identity.documento(), token, authorities);
				SecurityContextHolder.getContext().setAuthentication(auth);
			} catch (JwtException | IllegalArgumentException e) {
				log.warn("[SEGURIDAD] token rechazado: {}", e.getClass().getSimpleName());
				SecurityContextHolder.clearContext();
			}
		}
		chain.doFilter(request, response);
	}
}
