package com.seongmin.spike.alert.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertPolicyRepository extends JpaRepository<AlertPolicy, Long> {
    List<AlertPolicy> findByEnabledTrue();
    List<AlertPolicy> findByProjectIdOrderByIdAsc(Long projectId);
}
