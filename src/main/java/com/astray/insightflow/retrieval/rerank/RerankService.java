package com.astray.insightflow.retrieval.rerank;

import com.astray.insightflow.retrieval.model.Evidence;

import java.util.List;

public interface RerankService {

    boolean isEnabled();

    int candidateCount();

    List<Evidence> rerank(List<String> queries, List<Evidence> candidates);
}
