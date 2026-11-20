package com.backend.sigcapi.security;

import static org.junit.jupiter.api.Assertions.*;

import java.security.Key;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-with-at-least-thirty-two-characters";
    private JwtService jwt;

    @BeforeEach
    void setUp() {
        jwt = new JwtService();
        ReflectionTestUtils.setField(jwt, "SECRET_KEY", SECRET);
    }

    @Test
    @DisplayName("Un token generado es válido y conserva el documento del usuario")
    void tokenValidoYUsuario() {
        String token = jwt.generateToken("12345678");
        assertTrue(jwt.isTokenValid(token));
        assertEquals("12345678", jwt.extractUsername(token));
    }

    @Test
    @DisplayName("El token incluye el rol como claim \"roles\" para que el gateway autorice")
    void tokenConRol() {
        String token = jwt.generateToken("12345678", "ADMIN");
        Object roles = Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(SECRET.getBytes())).build()
                .parseClaimsJws(token).getBody().get("roles");
        assertEquals(java.util.List.of("ADMIN"), roles);
    }

    @Test
    @DisplayName("Un token alterado se rechaza")
    void tokenAlterado() {
        String token = jwt.generateToken("12345678");
        String[] partes = token.split("\\.");
        char primero = partes[2].charAt(0);
        partes[2] = (primero == 'A' ? 'B' : 'A') + partes[2].substring(1);
        String alterado = String.join(".", partes);
        assertFalse(jwt.isTokenValid(alterado));
    }

    @Test
    @DisplayName("Un token firmado con otra clave se rechaza")
    void tokenOtraClave() {
        Key otra = Keys.hmacShaKeyFor("another-secret-key-with-at-least-thirty-two-chars".getBytes());
        String token = Jwts.builder().setSubject("12345678")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otra, SignatureAlgorithm.HS256).compact();
        assertFalse(jwt.isTokenValid(token));
    }

    @Test
    @DisplayName("Un token expirado se rechaza")
    void tokenExpirado() {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes());
        String token = Jwts.builder().setSubject("12345678")
                .setIssuedAt(new Date(System.currentTimeMillis() - 120_000))
                .setExpiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(key, SignatureAlgorithm.HS256).compact();
        assertFalse(jwt.isTokenValid(token));
    }

    @Test
    @DisplayName("Texto que no es un JWT se rechaza")
    void tokenBasura() {
        assertFalse(jwt.isTokenValid("esto-no-es-un-jwt"));
        assertFalse(jwt.isTokenValid(""));
    }
}
