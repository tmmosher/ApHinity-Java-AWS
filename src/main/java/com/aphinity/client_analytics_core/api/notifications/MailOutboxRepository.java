package com.aphinity.client_analytics_core.api.notifications;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MailOutboxRepository extends JpaRepository<MailOutboxMessage, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select message from MailOutboxMessage message
        where message.id = :id
        """)
    Optional<MailOutboxMessage> lockById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select message from MailOutboxMessage message
        where message.consumedAt is null
          and message.failedAt is null
          and message.attemptCount < :maxAttempts
          and (message.nextAttemptAt is null or message.nextAttemptAt <= :now)
        order by message.createdAt asc
        """)
    List<MailOutboxMessage> lockDueForDelivery(
        @Param("now") Instant now,
        @Param("maxAttempts") int maxAttempts,
        Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select message from MailOutboxMessage message
        where message.consumedAt is null
          and message.failedAt is null
          and message.attemptCount >= :maxAttempts
          and (message.nextAttemptAt is null or message.nextAttemptAt <= :now)
        order by message.createdAt asc
        """)
    List<MailOutboxMessage> lockTerminalizationCandidates(
        @Param("now") Instant now,
        @Param("maxAttempts") int maxAttempts,
        Pageable pageable
    );
}
