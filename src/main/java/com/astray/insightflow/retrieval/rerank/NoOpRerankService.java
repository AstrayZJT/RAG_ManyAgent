package com.astray.insightflow.retrieval.rerank;

import com.astray.insightflow.retrieval.model.Evidence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(prefix = "rag.rerank", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpRerankService implements RerankService {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public int candidateCount() {
        return 0;
    }

    @Override
    public List<Evidence> rerank(List<String> queries, List<Evidence> candidates) {
        return candidates;
    }
}
