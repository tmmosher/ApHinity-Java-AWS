package com.aphinity.client_analytics_core.api.auth.services;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

/** JDBC adapter retaining atomic consume-once semantics. */
@Repository
public class JdbcOneTimeCodeStore implements OneTimeCodeStore {
    private final JdbcTemplate jdbcTemplate;

    public JdbcOneTimeCodeStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean replaceActiveCode(
        Long userId, String tokenHash, Instant expiresAt, Instant now, Channel channel
    ) {
        String table = table(channel);
        jdbcTemplate.update(
            "update " + table + " set consumed_at = ? where user_id = ? and consumed_at is null",
            Timestamp.from(now), userId
        );
        try {
            jdbcTemplate.update(
                "insert into " + table + " (user_id, token_hash, expires_at) values (?, ?, ?)",
                userId, tokenHash, Timestamp.from(expiresAt)
            );
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    @Override
    public boolean consume(Long userId, String tokenHash, Instant now, Channel channel) {
        String table = table(channel);
        Long tokenId;
        Instant expiresAt;
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                "select id, expires_at from " + table + " where user_id = ? and token_hash = ? and consumed_at is null",
                userId, tokenHash
            );
            tokenId = ((Number) row.get("id")).longValue();
            expiresAt = ((Timestamp) row.get("expires_at")).toInstant();
        } catch (EmptyResultDataAccessException ex) {
            return false;
        }
        int updated = jdbcTemplate.update(
            "update " + table + " set consumed_at = ? where id = ? and consumed_at is null",
            Timestamp.from(now), tokenId
        );
        return updated != 0 && expiresAt.isAfter(now);
    }

    private String table(Channel channel) {
        return switch (channel) {
            case EMAIL_VERIFICATION -> "email_verification_token";
            case PASSWORD_RESET -> "password_reset_token";
        };
    }
}
