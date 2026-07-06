package com.astray.insightflow.agent.verifier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VerifiedClaimRepository extends JpaRepository<VerifiedClaimEntity, String> {

    List<VerifiedClaimEntity> findByTaskIdOrderByCreatedAtAsc(String taskId);

    void deleteByTaskId(String taskId);
}
