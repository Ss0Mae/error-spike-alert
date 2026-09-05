package com.seongmin.spike.error.domain;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ErrorEventRepository extends JpaRepository<ErrorEvent, Long> {
    Optional<ErrorEvent> findByProjectIdAndEventId(Long projectId, String eventId);

    @Query("""
            SELECT e FROM ErrorEvent e
            WHERE e.projectId = :projectId
              AND (:environment IS NULL OR e.environment = :environment)
              AND (:fingerprint IS NULL OR e.fingerprint = :fingerprint)
              AND (:requestId IS NULL OR e.requestId = :requestId)
              AND e.receivedAt >= :from AND e.receivedAt < :to
            ORDER BY e.receivedAt DESC, e.id DESC
            """)
    Page<ErrorEvent> search(@Param("projectId") Long projectId, @Param("environment") Environment environment,
                            @Param("fingerprint") String fingerprint, @Param("requestId") String requestId,
                            @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
