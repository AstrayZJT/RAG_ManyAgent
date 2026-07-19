package com.astray.insightflow.retrieval.service;

import java.util.List;

public interface SearchProvider {

    String name();

    List<SearchHit> search(String originalQuery, String searchQuery);
}
