package com.astray.insightflow.retrieval.service;

import com.astray.insightflow.config.AgentProperties;
import com.astray.insightflow.retrieval.domain.EvidenceRecord;
import com.astray.insightflow.retrieval.domain.EvidenceSourceType;
import com.astray.insightflow.retrieval.model.Evidence;
import com.astray.insightflow.retrieval.persistence.EvidenceRecordRepository;
import com.astray.insightflow.tool.WebFetchTool;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ExternalRetrievalService {

    private final WebFetchTool webFetchTool;
    private final EvidenceRecordRepository evidenceRecordRepository;
    private final int maxExternalResultsPerQuery;
    private final int maxExternalPages;
    private final int timeoutMs;
    private final String userAgent;

    public ExternalRetrievalService(WebFetchTool webFetchTool,
                                    EvidenceRecordRepository evidenceRecordRepository,
                                    AgentProperties agentProperties) {
        this.webFetchTool = webFetchTool;
        this.evidenceRecordRepository = evidenceRecordRepository;
        this.maxExternalResultsPerQuery = agentProperties.search().maxExternalResultsPerQuery();
        this.maxExternalPages = agentProperties.search().maxExternalPages();
        this.timeoutMs = agentProperties.search().timeoutMs();
        this.userAgent = agentProperties.search().userAgent();
    }

    @Transactional
    public List<Evidence> search(String taskId, List<String> queries, boolean enabled) {
        if (!enabled || queries == null || queries.isEmpty()) {
            evidenceRecordRepository.deleteByTaskIdAndSourceType(taskId, EvidenceSourceType.EXTERNAL);
            return List.of();
        }

        Map<String, SearchHit> deduplicatedHits = new LinkedHashMap<>();
        for (String query : queries) {
            if (!StringUtils.hasText(query)) {
                continue;
            }
            List<SearchHit> hits = searchDuckDuckGo(query.trim());
            for (SearchHit hit : hits) {
                deduplicatedHits.putIfAbsent(hit.normalizedUrl(), hit);
                if (deduplicatedHits.size() >= maxExternalPages * 2) {
                    break;
                }
            }
            if (deduplicatedHits.size() >= maxExternalPages * 2) {
                break;
            }
        }

        List<Evidence> result = new ArrayList<>();
        int rank = 0;
        for (SearchHit hit : deduplicatedHits.values()) {
            if (result.size() >= maxExternalPages) {
                break;
            }

            String title = hit.title();
            String url = hit.normalizedUrl();
            String snippet = hit.snippet();
            try {
                WebFetchTool.FetchedPage page = webFetchTool.fetchPage(taskId, "retrieveExternal", url);
                if (StringUtils.hasText(page.title())) {
                    title = page.title();
                }
                if (StringUtils.hasText(page.finalUrl())) {
                    url = page.finalUrl();
                }
                if (StringUtils.hasText(page.content())) {
                    snippet = page.content();
                }
            } catch (Exception ignored) {
                if (!StringUtils.hasText(snippet)) {
                    continue;
                }
            }

            if (!StringUtils.hasText(snippet)) {
                continue;
            }

            Evidence evidence = new Evidence();
            evidence.setId(UUID.randomUUID().toString());
            evidence.setTitle(truncate(title, 220));
            evidence.setSnippet(truncate(snippet, 3800));
            evidence.setUrl(url);
            evidence.setSourceType(EvidenceSourceType.EXTERNAL);
            evidence.setScore(score(url, rank));
            result.add(evidence);
            rank++;
        }

        evidenceRecordRepository.deleteByTaskIdAndSourceType(taskId, EvidenceSourceType.EXTERNAL);
        evidenceRecordRepository.saveAll(result.stream().map(evidence -> toRecord(taskId, evidence)).toList());
        return result;
    }

    private List<SearchHit> searchDuckDuckGo(String query) {
        try {
            Document document = Jsoup.connect("https://html.duckduckgo.com/html/")
                    .data("q", query)
                    .data("kl", "cn-zh")
                    .userAgent(userAgent)
                    .timeout(timeoutMs)
                    .referrer("https://duckduckgo.com/")
                    .get();

            List<SearchHit> hits = new ArrayList<>();
            for (Element result : document.select(".result")) {
                Element link = result.selectFirst("a.result__a");
                if (link == null) {
                    continue;
                }
                String title = normalizeWhitespace(link.text());
                String url = decodeDuckDuckGoUrl(link.attr("href"));
                if (!StringUtils.hasText(title) || !StringUtils.hasText(url)) {
                    continue;
                }

                String snippet = normalizeWhitespace(result.select(".result__snippet").text());
                String normalizedUrl = normalizeUrl(url);
                if (!StringUtils.hasText(normalizedUrl)) {
                    continue;
                }

                hits.add(new SearchHit(title, normalizedUrl, snippet, query));
                if (hits.size() >= maxExternalResultsPerQuery) {
                    break;
                }
            }
            return hits;
        } catch (IOException exception) {
            return List.of();
        }
    }

    private String decodeDuckDuckGoUrl(String href) {
        if (!StringUtils.hasText(href)) {
            return null;
        }
        String candidate = href.startsWith("//") ? "https:" + href : href;
        try {
            URI uri = new URI(candidate);
            if (uri.getHost() != null && uri.getHost().contains("duckduckgo.com")) {
                String query = uri.getRawQuery();
                if (query != null) {
                    for (String part : query.split("&")) {
                        String[] kv = part.split("=", 2);
                        if (kv.length == 2 && "uddg".equals(kv[0])) {
                            return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                        }
                    }
                }
            }
            return candidate;
        } catch (URISyntaxException exception) {
            return candidate;
        }
    }

    private String normalizeUrl(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return null;
        }
        try {
            URI uri = new URI(rawUrl.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                return rawUrl.trim();
            }
            String normalizedScheme = StringUtils.hasText(scheme) ? scheme.toLowerCase(Locale.ROOT) : "https";
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();
            String query = uri.getQuery();
            URI normalized = new URI(normalizedScheme, uri.getUserInfo(), normalizedHost, uri.getPort(), path, query, null);
            return normalized.toString();
        } catch (URISyntaxException exception) {
            return rawUrl.trim();
        }
    }

    private double score(String url, int rank) {
        double score = 0.86D - (rank * 0.05D);
        String lower = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (lower.contains(".gov") || lower.contains(".edu")) {
            score += 0.08D;
        } else if (lower.contains(".org") || lower.contains("news") || lower.contains("press") || lower.contains("marklines")) {
            score += 0.04D;
        } else if (lower.contains("zhihu") || lower.contains("toutiao") || lower.contains("weibo")) {
            score -= 0.05D;
        }
        return Math.max(0.42D, Math.min(0.95D, score));
    }

    private String normalizeWhitespace(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private EvidenceRecord toRecord(String taskId, Evidence evidence) {
        EvidenceRecord record = new EvidenceRecord();
        record.setId(evidence.getId());
        record.setTaskId(taskId);
        record.setSourceType(evidence.getSourceType());
        record.setTitle(evidence.getTitle());
        record.setSnippet(evidence.getSnippet());
        record.setUrl(evidence.getUrl());
        record.setDocumentId(evidence.getDocumentId());
        record.setChunkId(evidence.getChunkId());
        record.setScore(evidence.getScore());
        record.setCreatedAt(Instant.now());
        return record;
    }

    private record SearchHit(String title, String normalizedUrl, String snippet, String query) {
    }
}
