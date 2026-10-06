package com.bizpos.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret:dGVzdF9qd3Rfc2VjcmV0X2tleV9mb3JfZGV2ZWxvcG1lbnRfcHVycG9zZXNfb25seV8yNTZiaXRzX2xvbmc=}") String secret,
            @Value("${jwt.expiration-ms:86400000}") long expirationMs) {
        this.expirationMs = expirationMs;
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (Exception e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Sinh JWT token chứa username và role
     */
    public String generateToken(String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * Lấy username từ JWT token
     */
    public String getUsernameFromToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    /**
     * Lấy role từ JWT token
     */
    public String getRoleFromToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("role", String.class);
    }

    /**
     * Kiểm tra tính hợp lệ của token
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (MalformedJwtException ex) {
            log.error("JWT token không đúng định dạng: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            log.error("JWT token đã hết hạn: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.error("JWT token không được hỗ trợ: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.error("Chuỗi JWT claims rỗng: {}", ex.getMessage());
        } catch (Exception ex) {
            log.error("Lỗi xác thực JWT: {}", ex.getMessage());
        }
        return false;
    }
}
