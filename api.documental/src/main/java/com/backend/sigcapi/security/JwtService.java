package com.backend.sigcapi.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;

@Service
public class JwtService {

    @Value("${com.sigcapi.security.token.secret}")
    private String SECRET_KEY;

    // Método para obtener la llave de forma segura
    private Key getSigningKey() {
        byte[] keyBytes = SECRET_KEY.getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // Obtiene el document del token
    public String extractUsername(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public String generateToken(String document) {
        return generateToken(document, null);
    }

    /**
     * Genera el token con el rol como claim "roles" para que el gateway GraphQL pueda autorizar
     * sin consultar la base de datos. El rol no es información sensible; no se incluyen datos personales.
     */
    public String generateToken(String document, String rol) {
        return Jwts.builder()
                .setSubject(document)
                .claim("roles", rol == null ? java.util.List.of() : java.util.List.of(rol))
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 86400000)) // 24h
                .signWith(getSigningKey(), SignatureAlgorithm.HS256) // Uso de llave objeto
                .compact();
    }

    public boolean isTokenValid(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false; 
        }
    }
}
