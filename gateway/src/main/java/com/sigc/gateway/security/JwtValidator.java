package com.sigc.gateway.security;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Valida firma (HS256) y vigencia del JWT emitido por la API y extrae identidad y roles. */
@Component
public class JwtValidator {

	private final Key key;

	public JwtValidator(@Value("${app.security.jwt-secret}") String secret) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

	public record Identity(String documento, List<String> roles) {}

	/** @throws io.jsonwebtoken.JwtException si la firma no es válida o el token expiró. */
	public Identity validate(String token) {
		Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
		List<String> roles = new ArrayList<>();
		Object raw = claims.get("roles");
		if (raw instanceof Collection<?> c) {
			c.forEach(r -> roles.add(normalize(String.valueOf(r))));
		} else if (raw instanceof String s && !s.isBlank()) {
			roles.add(normalize(s));
		}
		return new Identity(claims.getSubject(), roles);
	}

	private static String normalize(String role) {
		String r = role.trim().toUpperCase();
		return r.startsWith("ROLE_") ? r.substring(5) : r;
	}
}
