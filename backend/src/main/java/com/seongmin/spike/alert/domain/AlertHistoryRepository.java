package com.seongmin.spike.alert.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertHistoryRepository extends JpaRepository<AlertHistory, Long> {
    boolean existsByDedupKey(String dedupKey);

    @Query("""
            SELECT h FROM AlertHistory h
            WHERE h.projectId = :projectId
              AND (:policyId IS NULL OR h.alertPolicyId = :policyId)
              AND (:status IS NULL OR h.status = :status)
            ORDER BY h.detectedAt DESC, h.id DESC
            """)
    Page<AlertHistory> search(@Param("projectId") Long projectId, @Param("policyId") Long policyId,
                              @Param("status") AlertStatus status, Pageable pageable);
}
