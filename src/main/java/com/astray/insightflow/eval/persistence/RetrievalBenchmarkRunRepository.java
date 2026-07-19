package com.astray.insightflow.eval.persistence;

import com.astray.insightflow.eval.domain.RetrievalBenchmarkRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RetrievalBenchmarkRunRepository extends JpaRepository<RetrievalBenchmarkRun, String> {

    List<RetrievalBenchmarkRun> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
