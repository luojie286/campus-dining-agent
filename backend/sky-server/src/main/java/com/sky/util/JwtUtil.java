package com.sky.util;

import com.sky.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtUtil {

    private static final String ISSUER = "campus-dining";

    private final SecretKey signingKey;
    private final Duration ttl;
    private final JwtParser jwtParser;

    public JwtUtil(JwtProperties jwtProperties) {
        this.signingKey = Keys.hmacShaKeyFor(
                jwtProperties.getSecretKey()
                        .getBytes(StandardCharsets.UTF_8)
        );

        this.ttl = jwtProperties.getTtl();

        this.jwtParser = Jwts.parser()
                .requireIssuer(ISSUER)
                .verifyWith(signingKey)
                .build();
    }

    public String createToken(Long userId, String role) {
        Instant now = Instant.now();

        return Jwts.builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseToken(String token) {
        return jwtParser
                .parseSignedClaims(token)
                .getPayload();
    }
}