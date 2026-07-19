package com.astray.insightflow.retrieval.service;

import com.astray.insightflow.config.AgentProperties;
import com.astray.insightflow.retrieval.domain.EvidenceRecord;
import com.astray.insightflow.retrieval.domain.EvidenceSourceType;
import com.astray.insightflow.retrieval.model.Evidence;
import com.astray.insightflow.retrieval.persistence.EvidenceRecordRepository;
import com.astray.insightflow.tool.WebFetchTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ExternalRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(ExternalRetrievalService.class);

    private static final Pattern YEAR_PATTERN = Pattern.compile("20\\d{2}");
    private static final Pattern ENGLISH_TOKEN_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9+._-]{1,}");
    private static final String[] POWER_GRID_SITES = {
            "nea.gov.cn", "stategrid.com.cn", "sgcc.com.cn", "csg.cn", "cec.org.cn"
    };
    private static final String[] DOMAIN_HINTS = {
            "电网", "国家电网", "南方电网", "特高压", "输电", "配电网", "电力市场",
            "调度", "新能源", "储能", "光伏", "风电", "竞品", "市场份额", "融资",
            "专利", "营收", "毛利", "客户", "自动化", "数字电网", "虚拟电厂"
    };
    private static final String[] GENERIC_TOKENS = {
            "请", "调研", "分析", "研究", "生成", "一份", "报告", "中文", "带引用",
            "置信度", "关于", "中国", "历史", "现状", "未来", "趋势", "结构", "说明"
    };

    private final WebFetchTool webFetchTool;
    private final EvidenceRecordRepository evidenceRecordRepository;
    private final int maxExternalPages;
    private final List<SearchProvider> searchProviders;

    @Autowired
    public ExternalRetrievalService(WebFetchTool webFetchTool,
                                    EvidenceRecordRepository evidenceRecordRepository,
                                    AgentProperties agentProperties,
                                    List<SearchProvider> searchProviders) {
        this.webFetchTool = webFetchTool;
        this.evidenceRecordRepository = evidenceRecordRepository;
        this.maxExternalPages = agentProperties.search().maxExternalPages();
        this.searchProviders = List.copyOf(searchProviders);
    }

    ExternalRetrievalService(WebFetchTool webFetchTool,
                             EvidenceRecordRepository evidenceRecordRepository,
                             AgentProperties agentProperties) {
        this(webFetchTool, evidenceRecordRepository, agentProperties, List.of());
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
            for (String searchQuery : expandSearchQueries(query.trim())) {
                List<SearchHit> hits = searchExternalSources(query.trim(), searchQuery);
                for (SearchHit hit : hits) {
                    if (!isRelevantHit(query, hit.title(), hit.snippet(), hit.normalizedUrl())) {
                        continue;
                    }
                    deduplicatedHits.putIfAbsent(hit.normalizedUrl(), hit);
                    if (deduplicatedHits.size() >= maxExternalPages * 2) {
                        break;
                    }
                }
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
            if (!isRelevantHit(hit.query(), title, snippet, url)) {
                continue;
            }

            Evidence evidence = new Evidence();
            evidence.setId(UUID.randomUUID().toString());
            evidence.setTitle(truncate(title, 220));
            evidence.setSnippet(truncate(snippet, 3800));
            evidence.setUrl(url);
            evidence.setSourceType(EvidenceSourceType.EXTERNAL);
            evidence.setScore(score(hit.query(), title, snippet, url, rank));
            result.add(evidence);
            rank++;
        }

        evidenceRecordRepository.deleteByTaskIdAndSourceType(taskId, EvidenceSourceType.EXTERNAL);
        evidenceRecordRepository.saveAll(result.stream().map(evidence -> toRecord(taskId, evidence)).toList());
        return result;
    }

    private List<String> expandSearchQueries(String query) {
        List<String> expanded = new ArrayList<>();
        expanded.add(query);
        if (isPowerGridQuery(query)) {
            for (String site : POWER_GRID_SITES) {
                expanded.add("site:" + site + " " + query);
            }
        }
        return expanded;
    }

    private List<SearchHit> searchExternalSources(String originalQuery, String searchQuery) {
        for (SearchProvider provider : searchProviders) {
            try {
                List<SearchHit> hits = provider.search(originalQuery, searchQuery);
                if (!hits.isEmpty()) {
                    return hits;
                }
            } catch (RuntimeException exception) {
                log.warn("Search provider {} failed: {}", provider.name(), exception.getMessage());
            }
        }
        return List.of();
    }

    boolean isRelevantHit(String query, String title, String snippet, String url) {
        List<QuerySignal> signals = extractSignals(query);
        if (signals.isEmpty()) {
            return true;
        }
        String titleText = normalizeWhitespace(title).toLowerCase(Locale.ROOT);
        String snippetText = normalizeWhitespace(snippet).toLowerCase(Locale.ROOT);
        String urlText = normalizeWhitespace(url).toLowerCase(Locale.ROOT);

        int strongMatches = 0;
        int domainMatches = 0;
        double score = 0D;
        for (QuerySignal signal : signals) {
            if (contains(titleText, signal.text())) {
                score += signal.strong() ? 1.2D : 0.5D;
                strongMatches += signal.strong() ? 1 : 0;
                domainMatches += signal.domain() ? 1 : 0;
                continue;
            }
            if (contains(snippetText, signal.text())) {
                score += signal.strong() ? 0.7D : 0.3D;
                strongMatches += signal.strong() ? 1 : 0;
                domainMatches += signal.domain() ? 1 : 0;
                continue;
            }
            if (contains(urlText, signal.text())) {
                score += signal.strong() ? 0.85D : 0.25D;
                strongMatches += signal.strong() ? 1 : 0;
                domainMatches += signal.domain() ? 1 : 0;
            }
        }
        return domainMatches >= 1 && (strongMatches >= 1 || score >= 1.4D);
    }

    private double score(String query, String title, String snippet, String url, int rank) {
        double score = 0.86D - (rank * 0.05D);
        String lower = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (lower.contains(".gov") || lower.contains(".edu")) {
            score += 0.08D;
        } else if (lower.contains(".org") || lower.contains("news") || lower.contains("press") || lower.contains("marklines")) {
            score += 0.04D;
        } else if (lower.contains("zhihu") || lower.contains("toutiao") || lower.contains("weibo")) {
            score -= 0.05D;
        }
        if (isRelevantHit(query, title, snippet, url)) {
            score += 0.06D;
        }
        return Math.max(0.42D, Math.min(0.95D, score));
    }

    private List<QuerySignal> extractSignals(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        String cleaned = normalizeWhitespace(query).toLowerCase(Locale.ROOT);
        Set<QuerySignal> signals = new LinkedHashSet<>();

        for (String hint : DOMAIN_HINTS) {
            if (contains(cleaned, hint)) {
                signals.add(new QuerySignal(hint.toLowerCase(Locale.ROOT), true, true));
            }
        }

        for (String token : cleaned.split("[\\s，。！？；、/\\\\|()（）:：\"“”《》\\-–—]+")) {
            addSignal(signals, token, false, inferDomainToken(token));
            for (String fragment : token.split("(?:的|与|和|及|以及|围绕|针对|请|调研|分析|生成|报告|发展|现状|未来|趋势|结构|说明)")) {
                addSignal(signals, fragment, false, inferDomainToken(fragment));
            }
        }

        Matcher yearMatcher = YEAR_PATTERN.matcher(cleaned);
        while (yearMatcher.find()) {
            signals.add(new QuerySignal(yearMatcher.group(), true, false));
        }

        Matcher englishMatcher = ENGLISH_TOKEN_PATTERN.matcher(cleaned);
        while (englishMatcher.find()) {
            signals.add(new QuerySignal(englishMatcher.group().toLowerCase(Locale.ROOT), true, true));
        }

        return new ArrayList<>(signals);
    }

    private void addSignal(Set<QuerySignal> signals, String rawToken, boolean forceStrong, boolean domain) {
        if (!StringUtils.hasText(rawToken)) {
            return;
        }
        String token = rawToken.trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(token) || isGenericToken(token) || token.length() < 2 || token.length() > 24) {
            return;
        }
        boolean strong = forceStrong || token.length() >= 4 || containsDigit(token);
        signals.add(new QuerySignal(token, strong, domain));
    }

    private boolean inferDomainToken(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        if (normalized.matches("20\\d{2}(年|([qQ][1-4])|([qQ][1-4]年))?")) {
            return false;
        }
        if (containsAny(normalized, DOMAIN_HINTS)) {
            return true;
        }
        return normalized.chars().anyMatch(Character::isLetter);
    }

    private boolean isPowerGridQuery(String query) {
        return containsAny(query == null ? "" : query.toLowerCase(Locale.ROOT), POWER_GRID_SITES) || containsAny(query, new String[]{
                "电网", "国家电网", "南方电网", "特高压", "输电", "配电网", "电力"
        });
    }

    private boolean containsAny(String text, String[] candidates) {
        if (!StringUtils.hasText(text) || candidates == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate) && text.contains(candidate.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean isGenericToken(String token) {
        if (!StringUtils.hasText(token)) {
            return true;
        }
        for (String genericToken : GENERIC_TOKENS) {
            if (genericToken.equalsIgnoreCase(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsDigit(String token) {
        for (int i = 0; i < token.length(); i++) {
            if (Character.isDigit(token.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean contains(String text, String token) {
        return StringUtils.hasText(text) && StringUtils.hasText(token) && text.contains(token.toLowerCase(Locale.ROOT));
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
        record.setRetrievalStrategy("external_web");
        record.setCreatedAt(Instant.now());
        return record;
    }

    record QuerySignal(String text, boolean strong, boolean domain) {
    }
}
