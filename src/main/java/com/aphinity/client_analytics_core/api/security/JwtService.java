package com.aphinity.client_analytics_core.api.security;

import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.entities.Role;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class JwtService {
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public JwtService(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    public String createAccessToken(AppUser user, Long sessionId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(jwtProperties.getAccessTokenTtlSeconds());
        List<String> roles = user.getRoles().stream()
            .map(Role::getName)
            .sorted(Comparator.naturalOrder())
            .toList();

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
            .issuer(jwtProperties.getIssuer())
            .subject(String.valueOf(user.getId()))
            .issuedAt(now)
            .expiresAt(expiresAt)
            .claim("email", user.getEmail())
            .claim("roles", roles);

        if (sessionId != null) {
            claims.claim("sid", sessionId);
        }

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    public long getAccessTokenTtlSeconds() {
        return jwtProperties.getAccessTokenTtlSeconds();
    }

    public long getRefreshTokenTtlSeconds() {
        return jwtProperties.getRefreshTokenTtlSeconds();
    }
}
